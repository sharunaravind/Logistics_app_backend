package com.example.controller;

// Make sure to import VehicleRequest and VehicleResponse from com.example.model
import com.example.model.VehicleRequest;
import com.example.model.VehicleResponse;
import com.example.model.UpdateVehicleDetailsRequest;
import com.example.model.PaginatedVehicleResponse; // For paginated listing
import com.example.model.PaginationMetadata;    // For pagination metadata
import com.example.model.VehicleStatus;         // For filter parameter

import com.example.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Min;
// import java.util.List; // No longer returning List<Vehicle> directly for the main list endpoint

@RestController
@RequestMapping("/api/v1/vehicles")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:5174"}) // Added your React dev port
@Validated // For validating @RequestParam constraints
public class VehicleController { // If you have VehiclesApi interface, implement it

    private final VehicleService vehicleService;

    @Autowired
    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    // POST /api/v1/vehicles
    // Corresponds to operationId: addVehicle
    @PostMapping
    public ResponseEntity<VehicleResponse> addVehicle(@Valid @RequestBody VehicleRequest vehicleRequest) {
        VehicleResponse savedVehicle = vehicleService.addVehicle(vehicleRequest);
        return new ResponseEntity<>(savedVehicle, HttpStatus.CREATED);
    }

    // GET /api/v1/vehicles/{registrationNumber}
    // Corresponds to operationId: getVehicleByRegistrationNumber
    @GetMapping("/{registrationNumber}")
    public ResponseEntity<VehicleResponse> getVehicleByRegistrationNumber(@PathVariable String registrationNumber) {
        VehicleResponse vehicle = vehicleService.getVehicleByRegistrationNumber(registrationNumber);
        return ResponseEntity.ok(vehicle); // Service method now throws EntityNotFoundException
    }

    // GET /api/v1/vehicles
    // Corresponds to operationId: listVehicles (Updated for pagination, filtering, sorting)
    @GetMapping
    public ResponseEntity<PaginatedVehicleResponse> listVehicles(
            @RequestParam(value = "limit", defaultValue = "20") @Min(1) int limit,
            @RequestParam(value = "offset", defaultValue = "0") @Min(0) int offset,
            @RequestParam(value = "sortBy", defaultValue = "registrationNumber") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "asc") String sortDirection,
            @RequestParam(value = "registrationNumber", required = false) String registrationNumberFilter,
            @RequestParam(value = "vehicleType", required = false) String vehicleTypeFilter,
            @RequestParam(value = "vehicleStatus", required = false) VehicleStatus vehicleStatusFilter, // Use Enum
            @RequestParam(value = "minCapacity", required = false) Integer minCapacityFilter,
            @RequestParam(value = "minAvailableCapacity", required = false) Integer minAvailableCapacityFilter) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        int pageNumber = offset / limit; // Calculate page number for Spring Data Pageable (0-indexed)
        Pageable pageable = PageRequest.of(pageNumber, limit, Sort.by(direction, sortBy));

        Page<VehicleResponse> vehiclePage = vehicleService.listVehicles(
                pageable, registrationNumberFilter, vehicleTypeFilter,
                vehicleStatusFilter, minCapacityFilter, minAvailableCapacityFilter
        );

        PaginationMetadata metadata = new PaginationMetadata();
        metadata.setTotalItems((int) vehiclePage.getTotalElements());
        metadata.setLimit(vehiclePage.getSize());
        metadata.setOffset((int) vehiclePage.getPageable().getOffset());
        // metadata.setTotalPages(vehiclePage.getTotalPages()); // Add if in your DTO

        PaginatedVehicleResponse response = new PaginatedVehicleResponse();
        response.setMetadata(metadata);
        response.setData(vehiclePage.getContent());

        return ResponseEntity.ok(response);
    }

    // DELETE /api/v1/vehicles/{registrationNumber}
    // Corresponds to operationId: deleteVehicle
    @DeleteMapping("/{registrationNumber}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable String registrationNumber) {
        vehicleService.deleteVehicle(registrationNumber); // Changed from deleteVehicleById
        return ResponseEntity.noContent().build();
    }

    // PUT /api/v1/vehicles/{registrationNumber}/details
    // Corresponds to operationId: updateVehicleDetails
    @PutMapping("/{registrationNumber}/details")
    public ResponseEntity<VehicleResponse> updateVehicleDetails(
            @PathVariable String registrationNumber,
            @Valid @RequestBody UpdateVehicleDetailsRequest updateRequest) {
        VehicleResponse updatedVehicle = vehicleService.updateVehicleDetails(registrationNumber, updateRequest);
        return ResponseEntity.ok(updatedVehicle);
    }
}
