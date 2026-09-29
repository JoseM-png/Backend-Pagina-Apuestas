package com.rifas.api.dtos;

public record PaymentResultRequest(
        String orderId,
        String transactionId,
        String reference,
        String status,
        String statusMessage,
        Long amountInCents,
        Double amount,
        String currency,
        String paymentMethodType,
        String customerEmail,
        String customerName,
        String customerPhone,
        String rawPayload
) {}
