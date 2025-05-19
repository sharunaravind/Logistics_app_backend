package com.example.entity;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "customer_id", referencedColumnName = "id")
    @NotNull(message = "Order customer cannot be null")
    private Customer customer;

    @NotNull(message = "Order date cannot be null")
    @Column(name = "order_date", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime orderDate;

    @NotNull(message = "Delivery type cannot be null")
    @Column(name = "delivery_type")
    private Integer deliveryType;

    @Embedded
    @NotNull(message = "Parcel details cannot be null")
    private ParcelDetails parcelDetails;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "source_street")),
            @AttributeOverride(name = "city", column = @Column(name = "source_city")),
            @AttributeOverride(name = "state", column = @Column(name = "source_state")),
            @AttributeOverride(name = "pinCode", column = @Column(name = "source_pin_code")),
            @AttributeOverride(name = "location", column = @Column(name = "source_location_coordinates", columnDefinition = "geometry(Point,4326)"))
    })
    @NotNull(message = "Source location cannot be null")
    private Address sourceLocation;

    @Column(name = "logistics_status")
    private String logisticsStatus;

    @Column(name = "internal_processing_status")
    private String internalProcessingStatus;

    @Column(name = "delivery_date", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime deliveryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_vehicle_registration_number", referencedColumnName = "registration_number", nullable = true)
    private Vehicle assignedVehicle;

    @Column(name = "route_sequence_number")
    private Integer routeSequenceNumber; // For ordering stops in a vehicle's route

    @Column(name = "delivery_deadline", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime deliveryDeadline; // Delivery deadline for the order

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (this.logisticsStatus == null) {
            this.logisticsStatus = "Pending"; // Default from OrderStatus enum
        }
        if (this.internalProcessingStatus == null) {
            this.internalProcessingStatus = "ReadyForDispatch"; // Default from InternalProcessingStatus enum
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Constructors
    public Order() {
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public OffsetDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(OffsetDateTime orderDate) { this.orderDate = orderDate; }
    public Integer getDeliveryType() { return deliveryType; }
    public void setDeliveryType(Integer deliveryType) { this.deliveryType = deliveryType; }
    public ParcelDetails getParcelDetails() { return parcelDetails; }
    public void setParcelDetails(ParcelDetails parcelDetails) { this.parcelDetails = parcelDetails; }
    public Address getSourceLocation() { return sourceLocation; }
    public void setSourceLocation(Address sourceLocation) { this.sourceLocation = sourceLocation; }
    public String getLogisticsStatus() { return logisticsStatus; }
    public void setLogisticsStatus(String logisticsStatus) { this.logisticsStatus = logisticsStatus; }
    public String getInternalProcessingStatus() { return internalProcessingStatus; }
    public void setInternalProcessingStatus(String internalProcessingStatus) { this.internalProcessingStatus = internalProcessingStatus; }
    public OffsetDateTime getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(OffsetDateTime deliveryDate) { this.deliveryDate = deliveryDate; }
    public Vehicle getAssignedVehicle() { return assignedVehicle; }
    public void setAssignedVehicle(Vehicle assignedVehicle) { this.assignedVehicle = assignedVehicle; }
    public Integer getRouteSequenceNumber() { return routeSequenceNumber; }
    public void setRouteSequenceNumber(Integer routeSequenceNumber) { this.routeSequenceNumber = routeSequenceNumber; }
    public OffsetDateTime getDeliveryDeadline() { return deliveryDeadline; }
    public void setDeliveryDeadline(OffsetDateTime deliveryDeadline) { this.deliveryDeadline = deliveryDeadline; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}