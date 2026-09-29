package com.rifas.api.dtos;

import com.rifas.api.models.Raffle;

public record AdminStatsResponse(
        Double totalRevenue,
        Double potentialRevenue,
        int soldTickets,
        int totalTickets,
        double progressPercentage,
        long completedOrders,
        long pendingOrders,
        long failedOrders,
        double conversionRate,
        Raffle activeRaffle
) {
}
