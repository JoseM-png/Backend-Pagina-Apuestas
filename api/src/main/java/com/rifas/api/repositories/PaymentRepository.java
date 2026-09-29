package com.rifas.api.repositories;

import com.rifas.api.models.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    List<Payment> findByOrderIdOrderByCreatedAtDesc(String orderId);

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findByReference(String reference);

    List<Payment> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'APPROVED'")
    Double sumApprovedPaymentsAmount();

    @Query("SELECT p FROM Payment p WHERE " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:search IS NULL OR " +
           "LOWER(p.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.customerEmail) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.reference) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.transactionId) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Payment> searchPayments(
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable
    );
}
