package com.rifas.api.dtos;

import java.time.LocalDateTime;

public record BonusWinnerDTO(
        String ticketNumber,
        String prizeDescription,
        String status, // "WON" (comprado y pagado), "PENDING" (en orden pendiente), "AVAILABLE" (no vendido)
        String winnerName,
        String winnerEmail,
        String winnerPhone,
        String winnerAddress,
        String orderId,
        LocalDateTime orderDate
) {
}
