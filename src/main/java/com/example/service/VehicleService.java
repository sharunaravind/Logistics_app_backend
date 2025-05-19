package com.example.service;

import com.example.entity.Vehicle;
import com.example.model.VehicleRequest; // Assuming you have this for creating/updating
import com.example.model.VehicleResponse;
import com.example.model.UpdateVehicleDetailsRequest;
import com.example.model.VehicleStatus; // From your OpenAPI spec
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List; // Keep if you have methods returning List<Vehicle>

public interface VehicleService {

    VehicleResponse addVehicle(VehicleRequest vehicleRequest);

    VehicleResponse getVehicleByRegistrationNumber(String registrationNumber);

    // Updated to support pagination, filtering, and sorting
    Page<VehicleResponse> listVehicles(
            Pageable pageable,
            String registrationNumberFilter,
            String vehicleTypeFilter,
            VehicleStatus vehicleStatusFilter, // Use the enum type
            Integer minCapacityFilter,
            Integer minAvailableCapacityFilter
    );

    void deleteVehicle(String registrationNumber);

    VehicleResponse updateVehicleDetails(String registrationNumber, UpdateVehicleDetailsRequest updateRequest);

    // Keep the old method if it's used internally by other services directly for entities
    List<Vehicle> getAllVehicles(); // This was your original method
    Vehicle getVehicleById(String id); // This was your original method
    Vehicle saveVehicle(Vehicle vehicle); // This was your original method
    void deleteVehicleById(String id); // This was your original method

}