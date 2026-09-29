package com.rifas.api.dtos;

import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderDTO(
        String id,
        String customerName,
        String customerEmail,
        String customerPhone,
        String customerAddress,
        Double totalAmount,
        String paymentStatus,
        String gatewayTransactionId,
        LocalDateTime createdAt,
        int ticketCount,
        List<String> tickets
) {
}
