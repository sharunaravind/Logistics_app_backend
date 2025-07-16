package com.example.service;
import com.example.entity.Order;
import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;

import com.example.entity.Address;
import com.example.entity.Order;
import com.example.entity.Vehicle;
import com.example.model.AddressRequest;
import com.example.model.AssignmentSummaryResponse;
import com.example.model.InternalProcessingStatus;
import com.example.model.OrderStatus;
import com.example.model.VehicleStatus;
import com.example.repository.OrderRepository;
import com.example.repository.VehicleRepository;

import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import javax.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import com.example.model.PaginatedVehicleAssignmentResponse;
import com.example.model.VehicleAssignment;
import com.example.model.VehicleAssignmentVehicleIdentifier;
import com.example.model.OrderResponse;
import com.example.model.PaginationMetadata;
import com.example.model.AssignOrdersRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;


@Service
public class AssignmentService {

    public class OrderClusterPoint implements Clusterable {
        private final Order order;
        private final double[] point;

        public OrderClusterPoint(Order order) {
            this.order = order;
            this.point = new double[] {
                    order.getCustomer().getDeliveryAddress().getLatitude(),
                    order.getCustomer().getDeliveryAddress().getLongitude()
            };
        }

        public Order getOrder() {
            return order;
        }

        @Override
        public double[] getPoint() {
            return this.point;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private static final Address DEPOT_LOCATION_CONFIG;
    private static final long FIXED_SERVICE_TIME_SECONDS = 10 * 60;
    private static final double LATE_DELIVERY_PENALTY_COST = 1000000.0;

    private static final Map<String, VehicleTypeConstraints> VEHICLE_TYPE_CONSTRAINTS = new HashMap<>();

    static {

        System.loadLibrary("jniortools");
        DEPOT_LOCATION_CONFIG = new Address();
        // UPDATED DEPOT TO COIMBATORE FOR MORE REALISTIC TESTING WITH COIMBATORE ORDERS
        DEPOT_LOCATION_CONFIG.setStreet("123, Main Depot Road"); // Example Coimbatore Depot Street
        DEPOT_LOCATION_CONFIG.setCity("Coimbatore");
        DEPOT_LOCATION_CONFIG.setState("Tamil Nadu");
        DEPOT_LOCATION_CONFIG.setPinCode("641001"); // Example Coimbatore Pincode

        VEHICLE_TYPE_CONSTRAINTS.put("BIKE", new VehicleTypeConstraints(2L * 60 * 60 + 30 * 60));
        VEHICLE_TYPE_CONSTRAINTS.put("VAN", new VehicleTypeConstraints(4L * 60 * 60));
        VEHICLE_TYPE_CONSTRAINTS.put("TRUCK", new VehicleTypeConstraints(6L * 60 * 60));
        VEHICLE_TYPE_CONSTRAINTS.put("DEFAULT", new VehicleTypeConstraints(3L * 60 * 60));
    }

    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final DirectionsApiHelperService directionsService;
    private final GeocodingService geocodingService;
    private final OrderService orderService;

    @Autowired
    public AssignmentService(OrderRepository orderRepository,
                             VehicleRepository vehicleRepository,
                             DirectionsApiHelperService directionsService,
                             @Qualifier("googleGeocodingService") GeocodingService geocodingService,
                             OrderService orderService) {
        this.orderRepository = orderRepository;
        this.vehicleRepository = vehicleRepository;
        this.directionsService = directionsService;
        this.geocodingService = geocodingService;
        this.orderService = orderService;
        ensureDepotGeocoded();
    }

    private synchronized void ensureDepotGeocoded() {
        if (DEPOT_LOCATION_CONFIG.getLocation() == null) {
            log.info("Geocoding depot location: {}, {}", DEPOT_LOCATION_CONFIG.getStreet(), DEPOT_LOCATION_CONFIG.getCity());
            AddressRequest depotAddressRequest = mapAddressEntityToRequest(DEPOT_LOCATION_CONFIG);
            GeocodingService.Coordinates depotCoords = this.geocodingService.geocode(depotAddressRequest);
            if (depotCoords != null) {
                DEPOT_LOCATION_CONFIG.setLocationFromDoubles(depotCoords.latitude, depotCoords.longitude);
                log.info("Depot geocoded to: Lat {}, Lon {}", depotCoords.latitude, depotCoords.longitude);
            } else {
                log.error("CRITICAL: Depot location could not be geocoded. Using default 0,0. Assignment accuracy will be severely affected.");
                DEPOT_LOCATION_CONFIG.setLocationFromDoubles(0.0, 0.0);
            }
        }
    }

    @Transactional
    public AssignmentSummaryResponse performBatchAssignment(AssignOrdersRequest assignOrdersRequest) {
        log.info("Starting advanced batch assignment process...");

        // STEP 1: FILTER & FETCH
        OffsetDateTime todayStart = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime todayEnd = todayStart.plusDays(1);

        List<Order> assignableOrders = orderRepository.findAll((root, query, cb) ->
                cb.and(
                        cb.equal(root.get("internalProcessingStatus"), "ReadyForDispatch"),
                        cb.isNull(root.get("assignedVehicle")),
                        cb.between(root.get("deliveryDeadline"), todayStart, todayEnd)
                )
        );

        if (assignableOrders.isEmpty()) {
            return createSummary(0, 0, "No orders due today are ready for dispatch.");
        }

        List<Vehicle> availableVehicles = vehicleRepository.findByVehicleStatus(VehicleStatus.AVAILABLE);
        if (availableVehicles.isEmpty()) {
            return createSummary(0, 0, "No vehicles are available.");
        }
        List<VehicleRouteState> vehicleStates = availableVehicles.stream()
                .map(VehicleRouteState::new)
                .collect(Collectors.toList());

        // STEP 2: CLUSTER
        ClusterResult clusterResult = performClustering(assignableOrders);
        List<Cluster<OrderClusterPoint>> rawClusters = clusterResult.clusters;
        List<Order> outliers = clusterResult.outliers;

        // STEP 3: PRIORITIZE
        List<PriorityCluster> priorityClusters = new ArrayList<>();
        for (Cluster<OrderClusterPoint> cluster : rawClusters) {
            priorityClusters.add(new PriorityCluster(cluster));
        }
        priorityClusters.sort(Comparator.comparing(PriorityCluster::getUrgency));

        // STEP 4: ASSIGN CLUSTERS TO VEHICLES
        for (PriorityCluster pCluster : priorityClusters) {
            List<Order> ordersInCluster = pCluster.getOrders();
            VehicleRouteState bestVehicle = findBestVehicleForCluster(ordersInCluster, vehicleStates);

            if (bestVehicle != null) {
                // A. Find Optimal Route using OR-Tools
                RouteSolution routeSolution = findOptimalRouteForCluster(ordersInCluster, DEPOT_LOCATION_CONFIG);

                if (routeSolution.isSolvable()) {
                    // B. Final Validation: Check total tour duration
                    long serviceTime = (long) ordersInCluster.size() * FIXED_SERVICE_TIME_SECONDS;
                    long totalDuration = routeSolution.getDurationSeconds() + serviceTime;

                    if (bestVehicle.canAccommodateDuration(totalDuration)) {
                        // C. Assignment is valid and confirmed!
                        bestVehicle.assignRoute(routeSolution.getRoute(), totalDuration);
                        log.info("ASSIGNED Cluster of {} orders to vehicle {}", ordersInCluster.size(), bestVehicle.vehicleEntity.getRegistrationNumber());
                    } else {
                        log.warn("Vehicle {} could handle capacity but NOT DURATION for cluster. Route time: {}s", bestVehicle.vehicleEntity.getRegistrationNumber(), totalDuration);
                    }
                }
            }
        }

        // STEP 5: HANDLE OUTLIERS
        // We use a pragmatic greedy approach:
        // 1. Sort outliers by the most urgent delivery deadline.
        // 2. For each outlier, find the cheapest (cost factor) available
        // vehicle that can handle it.
        log.info("Attempting to assign {} outlier orders.", outliers.size());
        if (!outliers.isEmpty()) {
          // Prioritize the most urgent outliers first
          outliers.sort(Comparator.comparing(Order::getDeliveryDeadline));

          // Get a mutable list of vehicles that are still available after
          // cluster assignment
          List<VehicleRouteState> availableVehicleStates =
              vehicleStates.stream()
                  .filter(vs -> !vs.isUtilized())
                  .collect(Collectors.toList());

          // Sort available vehicles by cost to ensure we use the cheapest first
          // (e.g., BIKE > VAN > TRUCK)
          availableVehicleStates.sort(Comparator.comparingDouble(
              v -> getCostFactorForVehicle(v.vehicleEntity.getVehicleType())));

          for (Order outlier : outliers) {
            boolean assigned = false;
            // Use an iterator to safely remove a vehicle from the list once
            // it's assigned
            Iterator<VehicleRouteState> vehicleIterator =
                availableVehicleStates.iterator();

            while (vehicleIterator.hasNext()) {
              VehicleRouteState vehicleState = vehicleIterator.next();
              Vehicle vehicle = vehicleState.vehicleEntity;

              // A. Check capacity
              float orderWeight =
                  outlier.getParcelDetails() != null &&
                          outlier.getParcelDetails().getWeight() != null
                      ? outlier.getParcelDetails().getWeight()
                      : 0f;
              float orderVolume =
                  outlier.getParcelDetails() != null &&
                          outlier.getParcelDetails().getVolumeM3() != null
                      ? outlier.getParcelDetails().getVolumeM3()
                      : 0f;

              boolean weightOk = orderWeight <= (vehicle.getCapacity() != null
                                                     ? vehicle.getCapacity()
                                                     : Float.MAX_VALUE);
              boolean volumeOk =
                  orderVolume <= (vehicle.getVolumeCapacity() != null
                                      ? vehicle.getVolumeCapacity()
                                      : Float.MAX_VALUE);

              if (weightOk && volumeOk) {
                // B. Check if vehicle can handle the simple round-trip duration
                // We calculate a simple Depot -> Outlier -> Depot route
                DirectionsApiHelperService.RouteDetails toOutlier =
                    directionsService.getRouteDetails(
                        DEPOT_LOCATION_CONFIG,
                        outlier.getCustomer().getDeliveryAddress(), null,
                        false);
                DirectionsApiHelperService.RouteDetails fromOutlier =
                    directionsService.getRouteDetails(
                        outlier.getCustomer().getDeliveryAddress(),
                        DEPOT_LOCATION_CONFIG, null, false);

                if (toOutlier != null && fromOutlier != null) {
                  long travelDuration =
                      toOutlier.durationSeconds + fromOutlier.durationSeconds;
                  long totalDuration =
                      travelDuration + FIXED_SERVICE_TIME_SECONDS;

                  if (vehicleState.canAccommodateDuration(totalDuration)) {
                    // C. Assign and commit
                    log.info("ASSIGNED Outlier Order {} to Vehicle {}",
                             outlier.getId(), vehicle.getRegistrationNumber());
                    vehicleState.assignRoute(Collections.singletonList(outlier),
                                             totalDuration);

                    // This vehicle is now used, remove it from consideration
                    // for other outliers
                    vehicleIterator.remove();
                    assigned = true;
                    break; // Move to the next outlier
                  }
                }
              }
            } // End of vehicle loop

            if (!assigned) {
              log.warn("Could not find a suitable vehicle for outlier order " +
                       "{}. It will remain unassigned.",
                       outlier.getId());
            }
          } // End of outlier loop
        }

        // STEP 6: FINALIZE & SAVE
        int assignmentsCreatedCount = 0;
        int vehiclesUtilizedCount = 0;
        for (VehicleRouteState vs : vehicleStates) {
            if (vs.isUtilized()) {
                vehiclesUtilizedCount++;
                Vehicle vehicleToUpdate = vs.vehicleEntity;
                int routeSequence = 1;
                for (Order orderToUpdate : vs.getAssignedRoute()) {
                    orderToUpdate.setAssignedVehicle(vehicleToUpdate);
                    orderToUpdate.setLogisticsStatus(OrderStatus.OUT_FOR_DELIVERY.getValue());
                    orderToUpdate.setRouteSequenceNumber(routeSequence++);
                    orderRepository.save(orderToUpdate);
                    assignmentsCreatedCount++;
                }
                vehicleToUpdate.setVehicleStatus(VehicleStatus.IN_TRANSIT);
                vehicleRepository.save(vehicleToUpdate);
            }
        }

        return createSummary(assignmentsCreatedCount, vehiclesUtilizedCount, "Batch assignment process completed.");
    }
    private String getInternalReadyStatusValue() {
        try {
            return InternalProcessingStatus.READY_FOR_DISPATCH.getValue();
        } catch (Exception e) {
            log.warn("Could not resolve InternalProcessingStatus.READY_FOR_DISPATCH. Using string 'ReadyForDispatch'. Verify generated enum name in com.example.model.InternalProcessingStatus", e);
            return "ReadyForDispatch";
        }
    }

    private AssignmentSummaryResponse createSummary(int created, int utilized, String message) {
        AssignmentSummaryResponse summary = new AssignmentSummaryResponse();
        summary.setAssignmentsCreated(created);
        summary.setVehiclesUtilized(utilized);
        summary.setMessage(message);
        return summary;
    }

    private AddressRequest mapAddressEntityToRequest(Address addressEntity) {
        if (addressEntity == null) return null;
        AddressRequest request = new AddressRequest();
        request.setStreet(addressEntity.getStreet());
        request.setCity(addressEntity.getCity());
        request.setState(addressEntity.getState());
        request.setPinCode(addressEntity.getPinCode());
        return request;
    }

    private double getCostFactorForVehicle(String vehicleType) {
        if (vehicleType == null) return 1.7;
        switch (vehicleType.toUpperCase()) {
            case "BIKE": return 1.0;
            case "VAN": return 1.5;
            case "TRUCK": return 2.0;
            default: return 1.7;
        }
    }

    private static class VehicleTypeConstraints {
        final long maxDurationSeconds;

        VehicleTypeConstraints(long maxDurationSeconds) {
            this.maxDurationSeconds = maxDurationSeconds;
        }
    }

    private static class VehicleRouteState {
        final Vehicle vehicleEntity;
        private boolean utilized = false;
        private List<Order> assignedRoute = new ArrayList<>();
        private long totalDurationSeconds = 0;

        public VehicleRouteState(Vehicle vehicleEntity) {
            this.vehicleEntity = vehicleEntity;
        }

        public boolean isUtilized() {
            return this.utilized;
        }

        public List<Order> getAssignedRoute() {
            return this.assignedRoute;
        }

        public void assignRoute(List<Order> route, long duration) {
            this.assignedRoute = route;
            this.totalDurationSeconds = duration;
            this.utilized = true;
        }

        public boolean canAccommodateDuration(long tourDurationSeconds) {
            VehicleTypeConstraints constraints = VEHICLE_TYPE_CONSTRAINTS.getOrDefault(
                this.vehicleEntity.getVehicleType() != null ? this.vehicleEntity.getVehicleType().toUpperCase() : "DEFAULT",
                VEHICLE_TYPE_CONSTRAINTS.get("DEFAULT")
            );
            return tourDurationSeconds <= constraints.maxDurationSeconds;
        }
    }

    @Transactional(readOnly = true)
    public PaginatedVehicleAssignmentResponse getGroupedAssignments(Pageable pageable, String vehicleStatusFilterString, String registrationNumberFilter) {
        log.info("Fetching grouped assignments. Page: {}, Size: {}. Status Filter: {}, Reg Filter: {}",
                pageable.getPageNumber(), pageable.getPageSize(), vehicleStatusFilterString, registrationNumberFilter);

        Specification<Vehicle> vehicleSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (vehicleStatusFilterString != null && !vehicleStatusFilterString.isEmpty()) {
                try {
                    VehicleStatus status = VehicleStatus.fromValue(vehicleStatusFilterString);
                    predicates.add(cb.equal(root.get("vehicleStatus"), status));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid vehicle status filter value: {}", vehicleStatusFilterString);
                    return cb.disjunction();
                }
            }
            if (registrationNumberFilter != null && !registrationNumberFilter.isEmpty()) {
                predicates.add(cb.equal(root.get("registrationNumber"), registrationNumberFilter));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Vehicle> candidateVehicles = vehicleRepository.findAll(vehicleSpec);
        List<VehicleAssignment> vehicleAssignmentsResultList = new ArrayList<>();

        for (Vehicle vehicle : candidateVehicles) {
            List<Order> assignedOrdersForVehicle = orderRepository.findByAssignedVehicle_RegistrationNumberAndLogisticsStatusOrderByRouteSequenceNumberAsc(
                    vehicle.getRegistrationNumber(),
                    OrderStatus.OUT_FOR_DELIVERY.getValue()
            );

            if (!assignedOrdersForVehicle.isEmpty()) {
                VehicleAssignment va = new VehicleAssignment();
                VehicleAssignmentVehicleIdentifier identifier = new VehicleAssignmentVehicleIdentifier();
                identifier.setRegistrationNumber(vehicle.getRegistrationNumber());
                identifier.setVehicleType(vehicle.getVehicleType());
                va.setVehicleIdentifier(identifier);

                List<OrderResponse> orderResponses = assignedOrdersForVehicle.stream()
                        .map(orderService::mapOrderEntityToResponse)
                        .collect(Collectors.toList());
                va.setAssignedOrders(orderResponses);
                vehicleAssignmentsResultList.add(va);
            }
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), vehicleAssignmentsResultList.size());
        List<VehicleAssignment> pageContent = new ArrayList<>();
        if (start < end) {
            pageContent = vehicleAssignmentsResultList.subList(start, end);
        }

        Page<VehicleAssignment> vehicleAssignmentsPage = new PageImpl<>(pageContent, pageable, vehicleAssignmentsResultList.size());

        PaginatedVehicleAssignmentResponse response = new PaginatedVehicleAssignmentResponse();
        PaginationMetadata metadata = new PaginationMetadata();
        metadata.setTotalItems((int) vehicleAssignmentsPage.getTotalElements());
        metadata.setLimit(vehicleAssignmentsPage.getSize());
        if (vehicleAssignmentsPage.getPageable().isPaged()){
            metadata.setOffset((int) vehicleAssignmentsPage.getPageable().getOffset());
        } else {
            metadata.setOffset(0);
        }

        response.setMetadata(metadata);
        response.setData(vehicleAssignmentsPage.getContent());

        return response;
    }

    private ClusterResult performClustering(List<Order> orders) {
        // A. Prepare Orders for Clustering
        List<OrderClusterPoint> clusterPoints = new ArrayList<>();
        List<Order> unclusterableOrders = new ArrayList<>(); // For orders with no coordinates

        for (Order order : orders) {
            if (order.getCustomer() != null &&
                    order.getCustomer().getDeliveryAddress() != null &&
                    order.getCustomer().getDeliveryAddress().getLatitude() != null) {
                clusterPoints.add(new OrderClusterPoint(order));
            } else {
                log.warn("Order {} cannot be clustered (missing coordinates).", order.getId());
                unclusterableOrders.add(order);
            }
        }

        // B. Execute Clustering (if there's anything to cluster)
        if (clusterPoints.isEmpty()) {
            return new ClusterResult(new ArrayList<>(), unclusterableOrders);
        }

        // ~2km radius, min 3 orders per cluster
        DBSCANClusterer<OrderClusterPoint> clusterer = new DBSCANClusterer<>(0.018, 3);
        List<Cluster<OrderClusterPoint>> rawClusters = clusterer.cluster(clusterPoints);

        // C. Convert back to List<Order> and handle outliers
        // DBSCAN doesn't explicitly return outliers, so we find them by checking which points were not assigned to any cluster.
        Set<OrderClusterPoint> allClusteredPoints = rawClusters.stream()
                .flatMap(c -> c.getPoints().stream())
                .collect(Collectors.toSet());

        List<Order> outliers = clusterPoints.stream()
                .filter(p -> !allClusteredPoints.contains(p))
                .map(OrderClusterPoint::getOrder)
                .collect(Collectors.toList());

        outliers.addAll(unclusterableOrders); // Add orders that couldn't be clustered at all

        log.info("Clustering found {} clusters and {} outlier orders.", rawClusters.size(), outliers.size());

        return new ClusterResult(rawClusters, outliers);
    }

    // We'll need a simple helper class to return both results
    private static class ClusterResult {
        final List<Cluster<OrderClusterPoint>> clusters;
        final List<Order> outliers;

        public ClusterResult(List<Cluster<OrderClusterPoint>> clusters, List<Order> outliers) {
            this.clusters = clusters;
            this.outliers = outliers;
        }
    }

    private VehicleRouteState findBestVehicleForCluster(List<Order> ordersInCluster, List<VehicleRouteState> availableVehicles) {
        if (ordersInCluster.isEmpty()) {
            return null;
        }

        // 1. Calculate the cluster's total requirements
        float totalWeight = 0f;
        float totalVolume = 0f;
        for (Order order : ordersInCluster) {
            if (order.getParcelDetails() != null) {
                totalWeight += (order.getParcelDetails().getWeight() != null ? order.getParcelDetails().getWeight() : 0f);
                totalVolume += (order.getParcelDetails().getVolumeM3() != null ? order.getParcelDetails().getVolumeM3() : 0f);
            }
        }

        log.debug("Evaluating cluster of {} orders. Total W: {}, V: {}", ordersInCluster.size(), totalWeight, totalVolume);

        // 2. Find the cheapest vehicle that can handle the load
        VehicleRouteState bestChoice = null;
        double lowestCost = Double.MAX_VALUE;

        // Sort vehicles by cost to prioritize cheaper ones (e.g., bikes first)
        availableVehicles.sort(Comparator.comparingDouble(v -> getCostFactorForVehicle(v.vehicleEntity.getVehicleType())));

        for (VehicleRouteState vehicleState : availableVehicles) {
            // Check if vehicle is already assigned in this batch
            if (vehicleState.isUtilized()) {
                continue;
            }

            // Check capacity
            boolean weightOk = totalWeight <= (vehicleState.vehicleEntity.getCapacity() != null ? vehicleState.vehicleEntity.getCapacity() : Float.MAX_VALUE);
            boolean volumeOk = totalVolume <= (vehicleState.vehicleEntity.getVolumeCapacity() != null ? vehicleState.vehicleEntity.getVolumeCapacity() : Float.MAX_VALUE);

            if (weightOk && volumeOk) {
                // This vehicle is a candidate. For now, we'll pick the first/cheapest one.
                // A more advanced check could estimate the total tour duration here.
                bestChoice = vehicleState;
                log.info("Found suitable vehicle {} for cluster.", bestChoice.vehicleEntity.getRegistrationNumber());
                break; // Since we sorted by cost, the first one we find is the best.
            }
        }

        return bestChoice;
    }



//    private RouteSolution findOptimalRouteForCluster(List<Order> ordersInCluster, Address depotLocation) {
//        if (ordersInCluster == null || ordersInCluster.isEmpty()) {
//            return new RouteSolution(new ArrayList<>(), 0);
//        }
//
//        List<Address> locations = new ArrayList<>();
//        locations.add(depotLocation); // Depot is index 0
//        ordersInCluster.forEach(order -> locations.add(order.getCustomer().getDeliveryAddress()));
//
//        final long[][] distanceMatrix = createDistanceMatrix(locations);
//
//        RoutingIndexManager manager = new RoutingIndexManager(distanceMatrix.length, 1, 0);
//        RoutingModel routing = new RoutingModel(manager);
//
//        final int transitCallbackIndex = routing.registerTransitCallback((long fromIndex, long toIndex) -> {
//            int fromNode = manager.indexToNode(fromIndex);
//            int toNode = manager.indexToNode(toIndex);
//            return distanceMatrix[fromNode][toNode];
//        });
//
//        routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);
//
//        RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters()
//            .toBuilder()
//            .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
//            .setTimeLimit(Duration.newBuilder().setSeconds(5).build())
//            .build();
//
//        Assignment solution = routing.solveWithParameters(searchParameters);
//
//        if (solution != null) {
//            List<Order> sortedRoute = new ArrayList<>();
//            long totalDuration = 0;
//            long index = routing.start(0);
//            while (!routing.isEnd(index)) {
//                long nextIndex = solution.value(routing.nextVar(index));
//                int nodeIndex = manager.indexToNode(index);
//                int nextNodeIndex = manager.indexToNode(nextIndex);
//
//                // Add the travel duration for this leg of the journey
//                totalDuration += distanceMatrix[nodeIndex][nextNodeIndex];
//
//                if (nodeIndex != 0) {
//                    sortedRoute.add(ordersInCluster.get(nodeIndex - 1));
//                }
//                index = nextIndex;
//            }
//            return new RouteSolution(sortedRoute, totalDuration);
//        } else {
//            log.warn("OR-Tools could not find a solution. Returning empty route.");
//            return new RouteSolution(new ArrayList<>(), 0);
//        }
//    }


    private RouteSolution findOptimalRouteForCluster(List<Order> ordersInCluster, Address depotLocation) {
        if (ordersInCluster == null || ordersInCluster.isEmpty()) {
            return new RouteSolution(new ArrayList<>(), 0);
        }

        List<Address> locations = new ArrayList<>();
        locations.add(depotLocation); // Depot is index 0
        ordersInCluster.forEach(order -> locations.add(order.getCustomer().getDeliveryAddress()));

        final long[][] distanceMatrix = createDistanceMatrix(locations);

        RoutingIndexManager manager = new RoutingIndexManager(distanceMatrix.length, 1, 0);
        RoutingModel routing = new RoutingModel(manager);

        final int transitCallbackIndex = routing.registerTransitCallback((long fromIndex, long toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);
            return distanceMatrix[fromNode][toNode];
        });

        routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);

        // **** THIS IS THE CORRECTED PART ****
        // Use the explicit class name 'com.google.ortools.constraintsolver.main'
        // to create the search parameters.
        RoutingSearchParameters searchParameters = com.google.ortools.constraintsolver.main.defaultRoutingSearchParameters()
                .toBuilder()
                .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(Duration.newBuilder().setSeconds(5).build())
                .build();

        Assignment solution = routing.solveWithParameters(searchParameters);

        if (solution != null) {
            List<Order> sortedRoute = new ArrayList<>();
            long totalDuration = 0;
            long index = routing.start(0);
            while (!routing.isEnd(index)) {
                long nextIndex = solution.value(routing.nextVar(index));
                int nodeIndex = manager.indexToNode(index);
                int nextNodeIndex = manager.indexToNode(nextIndex);

                // Add the travel duration for this leg of the journey
                totalDuration += distanceMatrix[nodeIndex][nextNodeIndex];

                if (nodeIndex != 0) { // Exclude the depot from the final order list
                    sortedRoute.add(ordersInCluster.get(nodeIndex - 1));
                }
                index = nextIndex;
            }
            return new RouteSolution(sortedRoute, totalDuration);
        } else {
            log.warn("OR-Tools could not find a solution. Returning empty route.");
            return new RouteSolution(new ArrayList<>(), 0);
        }
    }

    // You'll need this helper method to build the matrix.
    private long[][] createDistanceMatrix(List<Address> locations) {
        int size = locations.size();
        long[][] matrix = new long[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    matrix[i][j] = 0;
                } else {
                    DirectionsApiHelperService.RouteDetails details = directionsService.getRouteDetails(
                        locations.get(i), locations.get(j), null, false);
                    // Use duration in seconds as the cost.
                    matrix[i][j] = (details != null) ? details.durationSeconds : Long.MAX_VALUE;
                }
            }
        }
        return matrix;
    }

    // To hold a cluster's orders and its urgency score
    private static class PriorityCluster {
        private final List<Order> orders;
        private final OffsetDateTime urgency; // Earliest deadline in the cluster

        public PriorityCluster(Cluster<OrderClusterPoint> cluster) {
            this.orders = cluster.getPoints().stream().map(OrderClusterPoint::getOrder).collect(Collectors.toList());
            this.urgency = this.orders.stream()
                    .map(Order::getDeliveryDeadline)
                    .min(OffsetDateTime::compareTo)
                    .orElse(OffsetDateTime.MAX);
        }
        public List<Order> getOrders() { return orders; }
        public OffsetDateTime getUrgency() { return urgency; }
    }

    // To hold the result from the OR-Tools solver
    private static class RouteSolution {
        private final List<Order> route;
        private final long durationSeconds;

        public RouteSolution(List<Order> route, long durationSeconds) {
            this.route = route;
            this.durationSeconds = durationSeconds;
        }
        public boolean isSolvable() { return route != null && !route.isEmpty(); }
        public List<Order> getRoute() { return route; }
        public long getDurationSeconds() { return durationSeconds; }
    }
}