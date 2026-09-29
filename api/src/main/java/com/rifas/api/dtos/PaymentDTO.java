package com.rifas.api.dtos;

import java.time.LocalDateTime;

public record PaymentDTO(
        String id,
        String orderId,
        String transactionId,
        String reference,
        Long amountInCents,
        Double amount,
        String currency,
        String paymentMethodType,
        String status,
        String statusMessage,
        String customerEmail,
        String customerName,
        String customerPhone,
        String gateway,
        LocalDateTime paymentDate,
        LocalDateTime createdAt,
        String rawPayload
) {}
