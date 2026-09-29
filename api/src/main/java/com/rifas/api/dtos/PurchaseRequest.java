package com.rifas.api.dtos;

public record PurchaseRequest(
        Long raffleId,
        String customerName,
        String customerEmail,
        String customerPhone,
        String customerAddress,
        int amountOfTickets // Cuántos números quiere (ej. 1, 10, 50, 100)
) {}