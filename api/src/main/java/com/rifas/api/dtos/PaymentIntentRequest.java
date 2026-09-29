package com.rifas.api.dtos;

public record PaymentIntentRequest(
        String orderId,
        String reference,
        Long amountInCents,
        Double amount,
        String customerEmail,
        String customerName,
        String customerPhone
) {}
