package com.rifas.api.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "transaction_id")
    private String transactionId; // ID provisto por la pasarela (ej: Wompi 12345-xxxx)

    @Column(nullable = false, length = 140)
    private String reference; // Referencia única (ej: JR_ORDEN_{uuid}_{timestamp})

    @Column(name = "amount_in_cents")
    private Long amountInCents; // Monto en centavos (ej: 5000000 para $50.000 COP)

    @Column(name = "amount", nullable = false)
    private Double amount; // Monto en pesos COP

    @Column(length = 10)
    private String currency = "COP";

    @Column(name = "payment_method_type", length = 50)
    private String paymentMethodType; // CARD, NEQUI, BANCOLOMBIA_TRANSFER, PSE, etc.

    // Estados: PENDING, APPROVED, DECLINED, VOIDED, ERROR
    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "status_message", length = 255)
    private String statusMessage;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(length = 30)
    private String gateway = "WOMPI";

    @Column(name = "payment_date")
    private LocalDateTime paymentDate;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.paymentDate == null) {
            this.paymentDate = LocalDateTime.now();
        }
    }
}
