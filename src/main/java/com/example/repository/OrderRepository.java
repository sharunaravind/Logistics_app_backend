package com.example.repository;

import com.example.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String>, JpaSpecificationExecutor<Order> {

    Page<Order> findByLogisticsStatus(String logisticsStatus, Pageable pageable);
    Page<Order> findByInternalProcessingStatus(String internalProcessingStatus, Pageable pageable);
    Page<Order> findByCustomer_Id(String customerId, Pageable pageable);
    Page<Order> findByAssignedVehicle_RegistrationNumber(String registrationNumber, Pageable pageable);
    Page<Order> findByOrderDateBetween(OffsetDateTime startDate, OffsetDateTime endDate, Pageable pageable);

    List<Order> findByInternalProcessingStatusAndAssignedVehicleIsNull(String internalProcessingStatus, Sort sort);
    Page<Order> findByInternalProcessingStatusAndAssignedVehicleIsNull(String internalProcessingStatus, Pageable pageable);

    List<Order> findByAssignedVehicle_RegistrationNumberAndLogisticsStatusOrderByRouteSequenceNumberAsc(String registrationNumber, String logisticsStatus);

    @Query("SELECT o.logisticsStatus, COUNT(o) FROM Order o GROUP BY o.logisticsStatus")
    List<Object[]> countOrdersByLogisticsStatus();

    @Query("SELECT o FROM Order o WHERE (o.logisticsStatus = 'Delivered' AND FUNCTION('DATE', o.deliveryDate) = FUNCTION('DATE', :targetDate)) OR (o.logisticsStatus = 'Out for Delivery' AND FUNCTION('DATE', o.updatedAt) <= FUNCTION('DATE', :targetDate))")
    Page<Order> findShippingOverviewByDate(@Param("targetDate") OffsetDateTime targetDate, Pageable pageable);
}