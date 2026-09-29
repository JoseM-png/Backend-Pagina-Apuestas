package com.rifas.api.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "raffles") // Le indicamos cómo se llamará la tabla en PostgreSQL
@Data // Lombok genera Getters, Setters, toString, etc.
@NoArgsConstructor
@AllArgsConstructor
public class Raffle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer totalTickets;

    @Column(nullable = false)
    private Double ticketPrice;

    // Estado del sorteo (ACTIVE, FINISHED, DRAFT)
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "grand_prize", nullable = false)
    private String grandPrize;

    @Column(name = "draw_date")
    @Temporal(TemporalType.DATE)
    private Date drawDate;

    // NUEVO CAMPO: Guardará la lista de números y premios (Ej: [{"number":"0145","desc":"Moto"}])
    @Column(columnDefinition = "TEXT")
    private String secondaryPrizes;
}