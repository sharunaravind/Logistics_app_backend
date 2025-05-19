package com.example.service;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import javax.persistence.criteria.Predicate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private static final Address DEPOT_LOCATION_CONFIG;
    private static final long FIXED_SERVICE_TIME_SECONDS = 10 * 60;
    private static final double LATE_DELIVERY_PENALTY_COST = 1000000.0;

    private static final Map<String, VehicleTypeConstraints> VEHICLE_TYPE_CONSTRAINTS = new HashMap<>();

    static {
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
        log.info("Starting batch assignment process...");
        OffsetDateTime assignmentStartTime = OffsetDateTime.now();
        String readyStatusValue = getInternalReadyStatusValue();

        Specification<Order> assignableOrdersSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("internalProcessingStatus"), readyStatusValue));
            predicates.add(cb.isNull(root.get("assignedVehicle")));
            predicates.add(cb.isNotNull(root.get("deliveryDeadline")));
            predicates.add(cb.greaterThan(root.get("deliveryDeadline"), assignmentStartTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<Order> assignableOrders = orderRepository.findAll(assignableOrdersSpec);
        assignableOrders.sort(Comparator.comparing(Order::getDeliveryDeadline, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Order::getOrderDate, Comparator.nullsLast(Comparator.naturalOrder())));

        if (assignableOrders.isEmpty()) {
            log.info("No assignable orders found (check status, assignment, and future deadline).");
            return createSummary(0, 0, "No assignable orders available that meet criteria.");
        }
        log.info("Found {} orders eligible for assignment.", assignableOrders.size());

        List<Vehicle> availableVehicleEntities = vehicleRepository.findByVehicleStatus(VehicleStatus.AVAILABLE);
        if (availableVehicleEntities.isEmpty()) {
            log.info("No vehicles currently available for assignment.");
            return createSummary(0, 0, "No vehicles are currently available.");
        }
        log.info("Found {} vehicles available for assignment.", availableVehicleEntities.size());

        List<VehicleRouteState> vehicleStates = availableVehicleEntities.stream()
                .map(vehicle -> new VehicleRouteState(vehicle, DEPOT_LOCATION_CONFIG, assignmentStartTime, directionsService))
                .collect(Collectors.toList());

        int assignmentsCreatedCount = 0;

        for (Order order : assignableOrders) {
            log.debug("Evaluating order ID: {} (Deadline: {})", order.getId(), order.getDeliveryDeadline());
            if (order.getCustomer() == null || order.getCustomer().getDeliveryAddress() == null ||
                    order.getCustomer().getDeliveryAddress().getLocation() == null) {
                log.warn("Order {} is missing customer delivery address or valid coordinates. Skipping.", order.getId());
                continue;
            }
            if (order.getSourceLocation() == null || order.getSourceLocation().getLocation() == null) {
                log.warn("Order {} is missing source location or valid coordinates. Skipping.", order.getId());
            }


            VehicleRouteState bestVehicleForThisOrder = null;
            DirectionsApiHelperService.RouteDetails bestLegDetailsForThisOrder = null;
            OffsetDateTime bestArrivalTimeAtOrder = null;
            double minCostMetricForThisOrder = Double.MAX_VALUE;
            boolean wouldBeLateForBestOption = false;

            for (VehicleRouteState currentVehicleState : vehicleStates) {
                log.trace("Evaluating vehicle {} for order {}", currentVehicleState.vehicleEntity.getRegistrationNumber(), order.getId());
                if (currentVehicleState.isEffectivelyFullOrDone()) {
                    log.trace("Vehicle {} is effectively full or done.", currentVehicleState.vehicleEntity.getRegistrationNumber());
                    continue;
                }
                if (!currentVehicleState.canAccommodate(order)) {
                    log.trace("Vehicle {} cannot accommodate order {}.", currentVehicleState.vehicleEntity.getRegistrationNumber(), order.getId());
                    continue;
                }

                Address originForLeg = currentVehicleState.getLastStopLocation();
                Address destinationForLeg = order.getCustomer().getDeliveryAddress();
                log.trace("Calculating leg for vehicle {} from city {} to city {}", currentVehicleState.vehicleEntity.getRegistrationNumber(), originForLeg.getCity(), destinationForLeg.getCity());
                DirectionsApiHelperService.RouteDetails legToOrderDetails = directionsService.getRouteDetails(originForLeg, destinationForLeg, null, false);

                if (legToOrderDetails == null) {
                    log.warn("Could not calculate leg route for order {} with vehicle {}. Origin: {}, Dest: {}. Skipping this vehicle for this order.",
                            order.getId(), currentVehicleState.vehicleEntity.getRegistrationNumber(), originForLeg.getStreet(), destinationForLeg.getStreet());
                    continue;
                }
                log.trace("Leg to order {} for vehicle {}: Duration {}s, Distance {}m", order.getId(), currentVehicleState.vehicleEntity.getRegistrationNumber(), legToOrderDetails.durationSeconds, legToOrderDetails.distanceMeters);


                OffsetDateTime estimatedArrivalTimeAtThisOrder = currentVehicleState.getCurrentTimeAtLastStop().plusSeconds(legToOrderDetails.durationSeconds);
                OffsetDateTime estimatedDepartureTimeAfterService = estimatedArrivalTimeAtThisOrder.plusSeconds(FIXED_SERVICE_TIME_SECONDS);

                if (!currentVehicleState.canUndertakeAdditionalLeg(legToOrderDetails, order.getCustomer().getDeliveryAddress(), DEPOT_LOCATION_CONFIG)) {
                    continue;
                }

                double costFactor = getCostFactorForVehicle(currentVehicleState.vehicleEntity.getVehicleType());
                double currentRouteCostMetric = legToOrderDetails.distanceMeters * costFactor;
                boolean isPotentiallyLate = false;

                if (estimatedDepartureTimeAfterService.isAfter(order.getDeliveryDeadline())) {
                    currentRouteCostMetric += LATE_DELIVERY_PENALTY_COST;
                    isPotentiallyLate = true;
                    log.debug("Order {} (Deadline: {}) would be LATE (Est. Departure: {}) with vehicle {}. Penalty applied. New cost: {}",
                            order.getId(), order.getDeliveryDeadline(), estimatedDepartureTimeAfterService, currentVehicleState.vehicleEntity.getRegistrationNumber(), currentRouteCostMetric);
                } else {
                    log.trace("Order {} (Deadline: {}) ON TIME (Est. Departure: {}) with vehicle {}. Cost: {}",
                            order.getId(), order.getDeliveryDeadline(), estimatedDepartureTimeAfterService, currentVehicleState.vehicleEntity.getRegistrationNumber(), currentRouteCostMetric);
                }


                if (currentRouteCostMetric < minCostMetricForThisOrder) {
                    minCostMetricForThisOrder = currentRouteCostMetric;
                    bestVehicleForThisOrder = currentVehicleState;
                    bestLegDetailsForThisOrder = legToOrderDetails;
                    bestArrivalTimeAtOrder = estimatedArrivalTimeAtThisOrder;
                    wouldBeLateForBestOption = isPotentiallyLate;
                    log.debug("Tentative best assignment for order {}: Vehicle {}, CostMetric {}", order.getId(), currentVehicleState.vehicleEntity.getRegistrationNumber(), currentRouteCostMetric);
                }
            }

            if (bestVehicleForThisOrder != null) {
                int routeSequenceNum = bestVehicleForThisOrder.assignedOrders.size() + 1;
                order.setRouteSequenceNumber(routeSequenceNum);

                bestVehicleForThisOrder.addOrder(order, bestLegDetailsForThisOrder, bestArrivalTimeAtOrder);
                assignmentsCreatedCount++;
                log.info("ASSIGNED order {} (Seq: {}) to vehicle {}. ETA: {}{}. Vehicle load W: {}, V: {}. Vehicle Current Route Dist: {}m, Dur: {}s",
                        order.getId(), routeSequenceNum, bestVehicleForThisOrder.vehicleEntity.getRegistrationNumber(),
                        bestArrivalTimeAtOrder, (wouldBeLateForBestOption ? " (LATE)" : ""),
                        bestVehicleForThisOrder.currentLoadWeight, bestVehicleForThisOrder.currentLoadVolume,
                        bestVehicleForThisOrder.getCurrentRouteDistanceToLastStopMeters(),
                        bestVehicleForThisOrder.getCurrentRouteDurationToLastStopSeconds());
            } else {
                log.warn("Order {} could not be assigned (no suitable vehicle found after all checks).", order.getId());
            }
        }

        int finalVehiclesUtilizedCount = 0;
        for (VehicleRouteState vs : vehicleStates) {
            if (vs.isUtilized()) {
                finalVehiclesUtilizedCount++;
                Vehicle vehicleToUpdate = vs.vehicleEntity;
                log.info("Finalizing assignments for Vehicle {}: {} orders. Final Est. Total Tour Duration (incl. service & final return): {}s, Final Est. Total Tour Distance: {}m",
                        vehicleToUpdate.getRegistrationNumber(), vs.assignedOrders.size(),
                        vs.calculateFinalTotalDurationWithReturn(DEPOT_LOCATION_CONFIG),
                        vs.calculateFinalTotalDistanceWithReturn(DEPOT_LOCATION_CONFIG));


                for (Order orderToUpdate : vs.assignedOrders) {
                    orderToUpdate.setAssignedVehicle(vehicleToUpdate);
                    orderToUpdate.setLogisticsStatus(OrderStatus.OUT_FOR_DELIVERY.getValue());
                    orderRepository.save(orderToUpdate);
                }
                vehicleToUpdate.setVehicleStatus(VehicleStatus.IN_TRANSIT);
                if (vehicleToUpdate.getCapacity() != null) {
                    vehicleToUpdate.setAvailableCapacity(vehicleToUpdate.getCapacity() - vs.currentLoadWeight);
                }
                if (vehicleToUpdate.getVolumeCapacity() != null) {
                    vehicleToUpdate.setAvailableVolumeCapacity(vehicleToUpdate.getVolumeCapacity() - vs.currentLoadVolume);
                }
                vehicleRepository.save(vehicleToUpdate);
            }
        }

        return createSummary(assignmentsCreatedCount, finalVehiclesUtilizedCount,
                "Batch assignment process completed. Orders assigned: " + assignmentsCreatedCount);
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
        Vehicle vehicleEntity;
        List<Order> assignedOrders = new ArrayList<>();
        float currentLoadWeight = 0f;
        float currentLoadVolume = 0f;
        Address lastStopLocation;
        OffsetDateTime currentTimeAtLastStop;
        long currentRouteDurationToLastStopSeconds = 0L;
        long currentRouteDistanceToLastStopMeters = 0L;
        boolean utilized = false;
        DirectionsApiHelperService directionsService;
        boolean effectivelyFullOrDone = false;

        public VehicleRouteState(Vehicle vehicleEntity, Address depotLocation, OffsetDateTime batchStartTime, DirectionsApiHelperService directionsService) {
            this.vehicleEntity = vehicleEntity;
            this.lastStopLocation = depotLocation;
            this.currentTimeAtLastStop = batchStartTime;
            this.directionsService = directionsService;
        }

        public boolean isUtilized() { return utilized; }
        public boolean isEffectivelyFullOrDone() { return effectivelyFullOrDone; }
        public Address getLastStopLocation() { return lastStopLocation; }
        public OffsetDateTime getCurrentTimeAtLastStop() { return currentTimeAtLastStop; }
        public long getCurrentRouteDurationToLastStopSeconds() { return currentRouteDurationToLastStopSeconds; }
        public long getCurrentRouteDistanceToLastStopMeters() { return currentRouteDistanceToLastStopMeters; }

        public boolean canAccommodate(Order order) {
            if (order.getParcelDetails() == null) {
                log.warn("Order {} has no parcel details, cannot check capacity.", order.getId());
                return false;
            }
            Float orderWeight = order.getParcelDetails().getWeight() != null ? order.getParcelDetails().getWeight() : 0f;
            Float orderVolume = order.getParcelDetails().getVolumeM3() != null ? order.getParcelDetails().getVolumeM3() : 0f;

            float potentialNewLoadWeight = this.currentLoadWeight + orderWeight;
            float potentialNewLoadVolume = this.currentLoadVolume + orderVolume;

            Float totalWeightCapacity = this.vehicleEntity.getCapacity();
            Float totalVolumeCapacity = this.vehicleEntity.getVolumeCapacity();

            boolean weightOk = potentialNewLoadWeight <= (totalWeightCapacity != null ? totalWeightCapacity : Float.MAX_VALUE);
            boolean volumeOk = potentialNewLoadVolume <= (totalVolumeCapacity != null ? totalVolumeCapacity : Float.MAX_VALUE);

            if (!weightOk) log.debug("Vehicle {} fails weight capacity for order {}. Current batch load: {}, Order: {}, Vehicle Total Cap: {}", vehicleEntity.getRegistrationNumber(), order.getId(), this.currentLoadWeight, orderWeight, totalWeightCapacity);
            if (!volumeOk) log.debug("Vehicle {} fails volume capacity for order {}. Current batch load: {}, Order: {}, Vehicle Total Cap: {}", vehicleEntity.getRegistrationNumber(), order.getId(), this.currentLoadVolume, orderVolume, totalVolumeCapacity);

            return weightOk && volumeOk;
        }

        public boolean canUndertakeAdditionalLeg(DirectionsApiHelperService.RouteDetails legToOrder, Address orderDestination, Address depotLocation) {
            if (legToOrder == null) {
                log.warn("Vehicle {}: legToOrder is null, cannot check trip constraints.", vehicleEntity.getRegistrationNumber());
                return false;
            }
            if (orderDestination == null || orderDestination.getLocation() == null) {
                log.warn("Vehicle {}: orderDestination or its location is null, cannot calculate return for trip constraints.", vehicleEntity.getRegistrationNumber());
                return false;
            }
            if (depotLocation == null || depotLocation.getLocation() == null) {
                log.warn("Vehicle {}: depotLocation or its location is null, cannot calculate return for trip constraints.", vehicleEntity.getRegistrationNumber());
                return false;
            }


            VehicleTypeConstraints constraints = VEHICLE_TYPE_CONSTRAINTS.getOrDefault(
                    this.vehicleEntity.getVehicleType() != null ? this.vehicleEntity.getVehicleType().toUpperCase() : "DEFAULT",
                    VEHICLE_TYPE_CONSTRAINTS.get("DEFAULT")
            );

            DirectionsApiHelperService.RouteDetails legReturnToDepot = this.directionsService.getRouteDetails(orderDestination, depotLocation, null, false);
            if (legReturnToDepot == null) {
                log.warn("Vehicle {}: Could not calculate return trip from potential order at {} to depot. Assuming it might violate trip constraints.", vehicleEntity.getRegistrationNumber(), orderDestination.getCity());
                this.effectivelyFullOrDone = true;
                return false;
            }

            long potentialTotalDuration = this.currentRouteDurationToLastStopSeconds
                    + legToOrder.durationSeconds
                    + FIXED_SERVICE_TIME_SECONDS
                    + legReturnToDepot.durationSeconds;

            boolean durationOk = potentialTotalDuration <= constraints.maxDurationSeconds;
            if (!durationOk) {
                log.debug("Vehicle {} adding order {} would exceed max trip duration. Potential: {}s (CurrentRouteDur: {}s + LegToOrder: {}s + Service: {}s + ReturnLeg: {}s), Max: {}s",
                        vehicleEntity.getRegistrationNumber(), orderDestination.getCity(),
                        potentialTotalDuration,
                        this.currentRouteDurationToLastStopSeconds, legToOrder.durationSeconds, FIXED_SERVICE_TIME_SECONDS, legReturnToDepot.durationSeconds,
                        constraints.maxDurationSeconds);
                this.effectivelyFullOrDone = true;
            }
            return durationOk;
        }

        public void addOrder(Order order, DirectionsApiHelperService.RouteDetails legToOrder, OffsetDateTime arrivalAtOrder) {
            this.assignedOrders.add(order);
            if (order.getParcelDetails() != null) {
                if (order.getParcelDetails().getWeight() != null) this.currentLoadWeight += order.getParcelDetails().getWeight();
                if (order.getParcelDetails().getVolumeM3() != null) this.currentLoadVolume += order.getParcelDetails().getVolumeM3();
            }

            this.currentRouteDistanceToLastStopMeters += legToOrder.distanceMeters;
            this.currentRouteDurationToLastStopSeconds += legToOrder.durationSeconds + FIXED_SERVICE_TIME_SECONDS;

            this.lastStopLocation = order.getCustomer().getDeliveryAddress();
            this.currentTimeAtLastStop = arrivalAtOrder.plusSeconds(FIXED_SERVICE_TIME_SECONDS);
            this.utilized = true;

            VehicleTypeConstraints currentConstraints = VEHICLE_TYPE_CONSTRAINTS.getOrDefault(
                    this.vehicleEntity.getVehicleType() != null ? this.vehicleEntity.getVehicleType().toUpperCase() : "DEFAULT",
                    VEHICLE_TYPE_CONSTRAINTS.get("DEFAULT")
            );
            if (this.lastStopLocation != null && this.lastStopLocation.getLocation() != null && DEPOT_LOCATION_CONFIG.getLocation() != null) {
                DirectionsApiHelperService.RouteDetails finalReturnLeg = this.directionsService.getRouteDetails(this.lastStopLocation, DEPOT_LOCATION_CONFIG, null, false);
                if (finalReturnLeg != null) {
                    if ((this.currentRouteDurationToLastStopSeconds + finalReturnLeg.durationSeconds) > currentConstraints.maxDurationSeconds) {
                        this.effectivelyFullOrDone = true;
                        log.debug("Vehicle {} marked as effectively full/done after adding order {} due to trip duration limit with final return.", vehicleEntity.getRegistrationNumber(), order.getId());
                    }
                } else {
                    this.effectivelyFullOrDone = true;
                    log.warn("Vehicle {} marked as effectively full/done as return to depot from {} could not be calculated after adding order {}.", vehicleEntity.getRegistrationNumber(), this.lastStopLocation.getCity(), order.getId());
                }
            } else {
                this.effectivelyFullOrDone = true;
                log.warn("Vehicle {} marked as effectively full/done due to missing location data for final check after adding order {}.", vehicleEntity.getRegistrationNumber(), order.getId());
            }
        }

        public long calculateFinalTotalDurationWithReturn(Address depotLocation) {
            if (assignedOrders.isEmpty()) return 0;
            if (lastStopLocation == null || lastStopLocation.getLocation() == null || depotLocation == null || depotLocation.getLocation() == null) return currentRouteDurationToLastStopSeconds;
            DirectionsApiHelperService.RouteDetails returnLeg = directionsService.getRouteDetails(lastStopLocation, depotLocation, null, false);
            return currentRouteDurationToLastStopSeconds + (returnLeg != null ? returnLeg.durationSeconds : 0);
        }

        public long calculateFinalTotalDistanceWithReturn(Address depotLocation) {
            if (assignedOrders.isEmpty()) return 0;
            if (lastStopLocation == null || lastStopLocation.getLocation() == null || depotLocation == null || depotLocation.getLocation() == null) return currentRouteDistanceToLastStopMeters;
            DirectionsApiHelperService.RouteDetails returnLeg = directionsService.getRouteDetails(lastStopLocation, depotLocation, null, false);
            return currentRouteDistanceToLastStopMeters + (returnLeg != null ? returnLeg.distanceMeters : 0);
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
}