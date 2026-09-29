package com.rifas.api.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id; // Usamos UUID para que el link de pago sea algo como /pago/123e4567-e89b... y no /pago/1 (más seguro)

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerEmail; // Este es el dato clave para buscar los números después

    @Column(nullable = false)
    private String customerPhone;

    // NUEVO CAMPO: Dirección de entrega
    @Column(nullable = false)
    private String customerAddress;

    @Column(nullable = false)
    private Double totalAmount;

    // Estados: PENDING, COMPLETED, FAILED
    @Column(nullable = false, length = 20)
    private String paymentStatus;

    // Aquí guardaremos el ID de la transacción de Wompi, ePayco o MercadoPago
    private String gatewayTransactionId;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}