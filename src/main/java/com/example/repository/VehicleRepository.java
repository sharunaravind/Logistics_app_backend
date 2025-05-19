package com.example.repository;
import com.example.entity.Vehicle;
import com.example.model.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, String>, JpaSpecificationExecutor<Vehicle> {
    // Add custom query methods if needed
    List<Vehicle> findByVehicleStatusAndAvailableCapacityGreaterThanEqual(VehicleStatus status, Float minCapacity);
    List<Vehicle> findByVehicleStatus(VehicleStatus status);

}

