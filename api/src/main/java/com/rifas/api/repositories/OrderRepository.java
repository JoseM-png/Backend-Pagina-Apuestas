package com.rifas.api.repositories;

import com.rifas.api.models.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    
    List<Order> findByCustomerEmail(String customerEmail);

    List<Order> findByPaymentStatusAndCreatedAtBefore(String paymentStatus, LocalDateTime cutoffTime);

    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findByPaymentStatusOrderByCreatedAtDesc(String paymentStatus);

    @Query("SELECT o FROM Order o WHERE " +
            "(:status IS NULL OR o.paymentStatus = :status) AND " +
            "(:search IS NULL OR LOWER(o.customerName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(o.customerEmail) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(o.customerPhone) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(o.id) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Order> searchOrders(@Param("status") String status, @Param("search") String search, Pageable pageable);

    long countByPaymentStatus(String paymentStatus);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.paymentStatus = 'COMPLETED'")
    Double sumTotalCompletedRevenue();
}