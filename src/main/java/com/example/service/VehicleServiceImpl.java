package com.example.service;

import com.example.entity.Vehicle;
import com.example.model.VehicleRequest;
import com.example.model.VehicleResponse;
import com.example.model.UpdateVehicleDetailsRequest;
import com.example.model.VehicleStatus;
import com.example.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class VehicleServiceImpl implements VehicleService {

    private static final Logger log = LoggerFactory.getLogger(VehicleServiceImpl.class);

    private final VehicleRepository vehicleRepository;

    @Autowired
    public VehicleServiceImpl(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    @Transactional
    public VehicleResponse addVehicle(VehicleRequest vehicleRequest) {
        // ...
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber(vehicleRequest.getRegistrationNumber());
        vehicle.setVehicleType(vehicleRequest.getVehicleType());

        // Uses the new getters from your manually modified VehicleRequest DTO
        vehicle.setCapacity(vehicleRequest.getWeightCapacityKg()); // Entity's 'capacity' field for weight
        vehicle.setVolumeCapacity(vehicleRequest.getVolumeCapacityM3());

        vehicle.setAvailableCapacity(vehicleRequest.getWeightCapacityKg());
        vehicle.setAvailableVolumeCapacity(vehicleRequest.getVolumeCapacityM3());
        // ...
        Vehicle savedVehicle = vehicleRepository.save(vehicle);
        // ...
        return mapEntityToResponse(savedVehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse getVehicleByRegistrationNumber(String registrationNumber) {
        Vehicle vehicle = vehicleRepository.findById(registrationNumber)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found with registration number: " + registrationNumber));
        return mapEntityToResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VehicleResponse> listVehicles(
            Pageable pageable,
            String registrationNumberFilter,
            String vehicleTypeFilter,
            VehicleStatus vehicleStatusFilter,
            Integer minCapacityFilter,
            Integer minAvailableCapacityFilter) {

        log.info("Listing vehicles with Pageable: {}, RegNo: '{}', Type: '{}', Status: {}, MinCap: {}, MinAvailCap: {}",
                pageable, registrationNumberFilter, vehicleTypeFilter, vehicleStatusFilter, minCapacityFilter, minAvailableCapacityFilter);

        Specification<Vehicle> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (registrationNumberFilter != null && !registrationNumberFilter.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("registrationNumber")), "%" + registrationNumberFilter.toLowerCase() + "%"));
            }
            if (vehicleTypeFilter != null && !vehicleTypeFilter.isEmpty()) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("vehicleType")), vehicleTypeFilter.toLowerCase()));
            }
            if (vehicleStatusFilter != null) {
                predicates.add(criteriaBuilder.equal(root.get("vehicleStatus"), vehicleStatusFilter));
            }
            if (minCapacityFilter != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("capacity"), minCapacityFilter.floatValue()));
            }
            if (minAvailableCapacityFilter != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("availableCapacity"), minAvailableCapacityFilter.floatValue()));
            }
            // Add filters for volume capacity if needed

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<Vehicle> vehiclePage = vehicleRepository.findAll(spec, pageable);
        log.info("Found {} vehicles matching criteria. Total elements: {}", vehiclePage.getNumberOfElements(), vehiclePage.getTotalElements());

        return vehiclePage.map(this::mapEntityToResponse);
    }


    @Override
    @Transactional
    public void deleteVehicle(String registrationNumber) {
        Vehicle vehicle = vehicleRepository.findById(registrationNumber)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found with registration number: " + registrationNumber));

        if (VehicleStatus.IN_TRANSIT.equals(vehicle.getVehicleStatus())) {
            throw new IllegalStateException("Vehicle " + registrationNumber + " is 'In Transit' and cannot be removed.");
        }

        vehicle.setVehicleStatus(VehicleStatus.REMOVED);
        vehicle.setAvailableCapacity(0f);
        vehicle.setAvailableVolumeCapacity(0f);
        vehicleRepository.save(vehicle);
        log.info("Marked vehicle {} as REMOVED.", registrationNumber);
    }

    @Override
    @Transactional
    public VehicleResponse updateVehicleDetails(String registrationNumber, UpdateVehicleDetailsRequest updateRequest) {
        Vehicle vehicle = vehicleRepository.findById(registrationNumber)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found: " + registrationNumber));

        boolean updated = false;
        if (updateRequest.getCapacity() != null) {
            float loadDifference = (vehicle.getCapacity() != null ? vehicle.getCapacity() : 0f) -
                    (vehicle.getAvailableCapacity() != null ? vehicle.getAvailableCapacity() : 0f);

            vehicle.setCapacity(updateRequest.getCapacity());
            vehicle.setAvailableCapacity(updateRequest.getCapacity() - loadDifference);
            if (vehicle.getAvailableCapacity() < 0) vehicle.setAvailableCapacity(0f);

            updated = true;
            log.info("Updated capacity for vehicle {}", registrationNumber);
        }

        if (updateRequest.getVehicleStatus() != null) {
            vehicle.setVehicleStatus(updateRequest.getVehicleStatus());
            if (VehicleStatus.AVAILABLE.equals(updateRequest.getVehicleStatus())) {
                vehicle.setAvailableCapacity(vehicle.getCapacity());
                vehicle.setAvailableVolumeCapacity(vehicle.getVolumeCapacity());
            }
            updated = true;
            log.info("Updated status for vehicle {} to {}", registrationNumber, updateRequest.getVehicleStatus());
        }

        // Removed the problematic if condition that called getMinProperties()
        // if (!updated && /* some other condition if needed based on DTO having at least one field set */ ) {
        //    log.warn("Update request for vehicle {} received, but no recognized fields were updated.", registrationNumber);
        // }

        Vehicle savedVehicle = vehicleRepository.save(vehicle);
        return mapEntityToResponse(savedVehicle);
    }


    // --- Original methods returning entities (kept for internal use if needed by AssignmentService) ---
    @Override
    public List<Vehicle> getAllVehicles() {
        log.debug("Fetching all vehicle entities directly.");
        return vehicleRepository.findAll();
    }

    @Override
    public Vehicle getVehicleById(String id) {
        log.debug("Fetching vehicle entity by ID: {}", id);
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(id);
        return vehicleOpt.orElse(null);
    }

    @Override
    public Vehicle saveVehicle(Vehicle vehicle) {
        log.debug("Saving vehicle entity directly: {}", vehicle.getRegistrationNumber());
        return vehicleRepository.save(vehicle);
    }

    @Override
    public void deleteVehicleById(String id) {
        log.warn("Direct deletion by ID called for vehicle: {}. Consider using soft delete.", id);
        vehicleRepository.deleteById(id);
    }

    // --- Helper Mapper ---
    private VehicleResponse mapEntityToResponse(Vehicle vehicle) {
        if (vehicle == null) {
            return null;
        }
        VehicleResponse response = new VehicleResponse();
        response.setRegistrationNumber(vehicle.getRegistrationNumber());
        response.setVehicleType(vehicle.getVehicleType());
        response.setCapacity(vehicle.getCapacity());
        response.setVolumeCapacity(vehicle.getVolumeCapacity());
        response.setAvailableCapacity(vehicle.getAvailableCapacity());
        response.setAvailableVolumeCapacity(vehicle.getAvailableVolumeCapacity());
        response.setVehicleStatus(vehicle.getVehicleStatus());
        return response;
    }
}