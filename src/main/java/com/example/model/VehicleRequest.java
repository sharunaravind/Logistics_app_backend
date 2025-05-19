// MODIFIED com.example.model.VehicleRequest.java
package com.example.model; // Ensure your package is correct

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema; // Keep if you want Schema docs
import javax.validation.constraints.NotNull;      // For validation
import javax.validation.constraints.DecimalMin;   // For validation

// Keep the @Generated annotation if it was there
// import javax.annotation.Generated;
// @Generated(value = "org.openapitools.codegen.languages.SpringCodegen", ...)

public class VehicleRequest {

  @JsonProperty("registrationNumber") // Keep if present, or add if needed for JSON mapping
  private String registrationNumber;

  @JsonProperty("vehicleType")
  private String vehicleType;

  // Remove the old 'capacity' field
  // private Float capacity;

  @JsonProperty("weightCapacityKg") // New field
  private Float weightCapacityKg;

  @JsonProperty("volumeCapacityM3") // New field
  private Float volumeCapacityM3;

  // Default constructor (often needed by frameworks like Jackson)
  public VehicleRequest() {
    super();
  }

  /**
   * Constructor with required parameters (adjust based on what you deem required)
   */
  public VehicleRequest(String registrationNumber, String vehicleType, Float weightCapacityKg, Float volumeCapacityM3) {
    this.registrationNumber = registrationNumber;
    this.vehicleType = vehicleType;
    this.weightCapacityKg = weightCapacityKg;
    this.volumeCapacityM3 = volumeCapacityM3;
  }

  // Getters and Setters for registrationNumber and vehicleType (existing, ensure they are there)
  @NotNull
  @Schema(name = "registrationNumber", /* existing schema attributes */ requiredMode = Schema.RequiredMode.REQUIRED)
  public String getRegistrationNumber() {
    return registrationNumber;
  }
  public void setRegistrationNumber(String registrationNumber) {
    this.registrationNumber = registrationNumber;
  }

  @NotNull
  @Schema(name = "vehicleType", /* existing schema attributes */ requiredMode = Schema.RequiredMode.REQUIRED)
  public String getVehicleType() {
    return vehicleType;
  }
  public void setVehicleType(String vehicleType) {
    this.vehicleType = vehicleType;
  }

  // Remove getter/setter for old 'capacity' field
  // public Float getCapacity() { return capacity; }
  // public void setCapacity(Float capacity) { this.capacity = capacity; }

  // --- ADD Getters and Setters for new fields ---
  @NotNull // Make it required to match your entity
  @DecimalMin("0") // Match entity constraints if applicable
  @Schema(name = "weightCapacityKg", description = "Total weight carrying capacity in kilograms (kg).", example = "500.0", requiredMode = Schema.RequiredMode.REQUIRED)
  public Float getWeightCapacityKg() {
    return weightCapacityKg;
  }
  public void setWeightCapacityKg(Float weightCapacityKg) {
    this.weightCapacityKg = weightCapacityKg;
  }

  @NotNull // Make it required to match your entity's @NotNull for volumeCapacity
  @DecimalMin("0") // Match entity constraints if applicable
  @Schema(name = "volumeCapacityM3", description = "Total volume carrying capacity in cubic meters (m^3).", example = "2.5", requiredMode = Schema.RequiredMode.REQUIRED)
  public Float getVolumeCapacityM3() {
    return volumeCapacityM3;
  }
  public void setVolumeCapacityM3(Float volumeCapacityM3) {
    this.volumeCapacityM3 = volumeCapacityM3;
  }
  // --- END ADD Getters and Setters ---


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VehicleRequest that = (VehicleRequest) o;
    return Objects.equals(registrationNumber, that.registrationNumber) &&
            Objects.equals(vehicleType, that.vehicleType) &&
            Objects.equals(weightCapacityKg, that.weightCapacityKg) && // UPDATED
            Objects.equals(volumeCapacityM3, that.volumeCapacityM3);   // UPDATED
  }

  @Override
  public int hashCode() {
    return Objects.hash(registrationNumber, vehicleType, weightCapacityKg, volumeCapacityM3); // UPDATED
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class VehicleRequest {\n");
    sb.append("    registrationNumber: ").append(toIndentedString(registrationNumber)).append("\n");
    sb.append("    vehicleType: ").append(toIndentedString(vehicleType)).append("\n");
    sb.append("    weightCapacityKg: ").append(toIndentedString(weightCapacityKg)).append("\n"); // UPDATED
    sb.append("    volumeCapacityM3: ").append(toIndentedString(volumeCapacityM3)).append("\n"); // UPDATED
    sb.append("}");
    return sb.toString();
  }

  private String toIndentedString(Object o) {
    if (o == null) return "null";
    return o.toString().replace("\n", "\n    ");
  }
}