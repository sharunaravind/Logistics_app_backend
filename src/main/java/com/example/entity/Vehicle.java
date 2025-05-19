package com.example.entity;

import com.example.model.VehicleStatus; // Make sure this import is correct

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;


@Entity
@Table(name = "vehicle") // Ensures Hibernate uses the table named "vehicle" (singular)
public class Vehicle {

    @Id
    @Column(name = "registration_number", nullable = false, unique = true)
    private String registrationNumber;

    @NotNull
    private String vehicleType; // e.g., "Bike", "Van", "Truck"

    @NotNull
    @Column(name = "weight_capacity_kg") // Total weight capacity in KG
    private Float capacity;

    @NotNull
    @Column(name = "volume_capacity_m3") // Total volume capacity in cubic meters
    private Float volumeCapacity;

    @Column(name = "available_weight_capacity_kg")
    private Float availableCapacity; // Current available weight capacity

    @Column(name = "available_volume_capacity_m3")
    private Float availableVolumeCapacity; // Current available volume capacity

    @NotNull
    @Enumerated(EnumType.STRING)
    private VehicleStatus vehicleStatus = VehicleStatus.AVAILABLE; // Default to AVAILABLE

    // Constructors
    public Vehicle() {
    }

    // Getters and Setters
    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public Float getCapacity() { // Represents total weight capacity
        return capacity;
    }

    public void setCapacity(Float capacity) {
        this.capacity = capacity;
    }

    public Float getVolumeCapacity() {
        return volumeCapacity;
    }

    public void setVolumeCapacity(Float volumeCapacity) {
        this.volumeCapacity = volumeCapacity;
    }

    public Float getAvailableCapacity() { // Represents available weight capacity
        return availableCapacity;
    }

    public void setAvailableCapacity(Float availableCapacity) {
        this.availableCapacity = availableCapacity;
    }

    public Float getAvailableVolumeCapacity() {
        return availableVolumeCapacity;
    }

    public void setAvailableVolumeCapacity(Float availableVolumeCapacity) {
        this.availableVolumeCapacity = availableVolumeCapacity;
    }

    public VehicleStatus getVehicleStatus() {
        return vehicleStatus;
    }

    public void setVehicleStatus(VehicleStatus vehicleStatus) {
        this.vehicleStatus = vehicleStatus;
    }
}