package com.rifas.api.services;

import com.rifas.api.dtos.*;
import com.rifas.api.models.Order;
import com.rifas.api.models.Raffle;
import com.rifas.api.models.Ticket;
import com.rifas.api.repositories.OrderRepository;
import com.rifas.api.repositories.RaffleRepository;
import com.rifas.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final RaffleRepository raffleRepository;
    private final OrderService orderService;

    // Clave maestra para acceso administrativo (configurable o por defecto)
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin123";
    private static final String ADMIN_TOKEN_PREFIX = "JR_ELITE_ADMIN_SESSION_";

    public AdminLoginResponse login(AdminLoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            return new AdminLoginResponse(false, null, null, null, "Credenciales inválidas");
        }

        if (ADMIN_USER.equalsIgnoreCase(request.username().trim()) && ADMIN_PASS.equals(request.password().trim())) {
            String token = ADMIN_TOKEN_PREFIX + UUID.randomUUID().toString().replace("-", "");
            return new AdminLoginResponse(true, token, ADMIN_USER, "SUPER_ADMIN", "Acceso concedido al Panel Élite");
        }

        return new AdminLoginResponse(false, null, null, null, "Usuario o contraseña incorrectos");
    }

    public AdminStatsResponse getDashboardStats() {
        Optional<Raffle> activeRaffleOpt = raffleRepository.findFirstByOrderByIdDesc();
        Raffle activeRaffle = activeRaffleOpt.orElse(null);

        int soldTickets = 0;
        int totalTickets = 0;
        double potentialRevenue = 0.0;

        if (activeRaffle != null) {
            totalTickets = activeRaffle.getTotalTickets() != null ? activeRaffle.getTotalTickets() : 0;
            soldTickets = ticketRepository.countByRaffleId(activeRaffle.getId());
            potentialRevenue = totalTickets * (activeRaffle.getTicketPrice() != null ? activeRaffle.getTicketPrice() : 0.0);
        }

        Double totalRevenue = orderRepository.sumTotalCompletedRevenue();
        if (totalRevenue == null) totalRevenue = 0.0;

        long completed = orderRepository.countByPaymentStatus("COMPLETED");
        long pending = orderRepository.countByPaymentStatus("PENDING");
        long failed = orderRepository.countByPaymentStatus("FAILED");

        long totalOrders = completed + pending + failed;
        double conversionRate = totalOrders > 0 ? ((double) completed / totalOrders) * 100.0 : 0.0;
        double progressPercentage = totalTickets > 0 ? ((double) soldTickets / totalTickets) * 100.0 : 0.0;

        return new AdminStatsResponse(
                totalRevenue,
                potentialRevenue,
                soldTickets,
                totalTickets,
                Math.round(progressPercentage * 100.0) / 100.0,
                completed,
                pending,
                failed,
                Math.round(conversionRate * 100.0) / 100.0,
                activeRaffle
        );
    }

    public Page<AdminOrderDTO> getOrders(String status, String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by("createdAt").descending());
        
        String cleanStatus = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) ? status.trim().toUpperCase() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<Order> orderPage = orderRepository.searchOrders(cleanStatus, cleanSearch, pageable);

        return orderPage.map(order -> {
            List<String> tickets = ticketRepository.findByOrderId(order.getId())
                    .stream()
                    .map(Ticket::getTicketNumber)
                    .toList();

            return new AdminOrderDTO(
                    order.getId(),
                    order.getCustomerName(),
                    order.getCustomerEmail(),
                    order.getCustomerPhone(),
                    order.getCustomerAddress(),
                    order.getTotalAmount(),
                    order.getPaymentStatus(),
                    order.getGatewayTransactionId(),
                    order.getCreatedAt(),
                    tickets.size(),
                    tickets
            );
        });
    }

    public AdminOrderDTO getOrderDetails(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada: " + orderId));

        List<String> tickets = ticketRepository.findByOrderId(order.getId())
                .stream()
                .map(Ticket::getTicketNumber)
                .toList();

        return new AdminOrderDTO(
                order.getId(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getCustomerPhone(),
                order.getCustomerAddress(),
                order.getTotalAmount(),
                order.getPaymentStatus(),
                order.getGatewayTransactionId(),
                order.getCreatedAt(),
                tickets.size(),
                tickets
        );
    }

    @Transactional
    public void approveOrder(String orderId) {
        orderService.approveOrderManually(orderId);
    }

    @Transactional
    public void cancelOrder(String orderId) {
        orderService.cancelOrder(orderId);
    }

    public List<BonusWinnerDTO> getBonusesStatus() {
        Optional<Raffle> activeRaffleOpt = raffleRepository.findFirstByOrderByIdDesc();
        if (activeRaffleOpt.isEmpty()) {
            return Collections.emptyList();
        }

        Raffle activeRaffle = activeRaffleOpt.get();
        String secondaryPrizesJson = activeRaffle.getSecondaryPrizes();
        if (secondaryPrizesJson == null || secondaryPrizesJson.isBlank()) {
            return Collections.emptyList();
        }

        List<BonusEntry> prizesList = parseSecondaryPrizes(secondaryPrizesJson);
        if (prizesList.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> ticketNumbers = prizesList.stream().map(BonusEntry::number).toList();
        List<Ticket> foundTickets = ticketRepository.findByRaffleIdAndTicketNumberIn(activeRaffle.getId(), ticketNumbers);

        Map<String, Ticket> ticketMap = new HashMap<>();
        for (Ticket t : foundTickets) {
            ticketMap.put(t.getTicketNumber(), t);
        }

        List<BonusWinnerDTO> result = new ArrayList<>();
        for (BonusEntry entry : prizesList) {
            Ticket t = ticketMap.get(entry.number());
            if (t != null && t.getOrder() != null) {
                Order order = t.getOrder();
                String status = "COMPLETED".equals(order.getPaymentStatus()) ? "WON" : "PENDING";
                result.add(new BonusWinnerDTO(
                        entry.number(),
                        entry.desc(),
                        status,
                        order.getCustomerName(),
                        order.getCustomerEmail(),
                        order.getCustomerPhone(),
                        order.getCustomerAddress(),
                        order.getId(),
                        order.getCreatedAt()
                ));
            } else {
                result.add(new BonusWinnerDTO(
                        entry.number(),
                        entry.desc(),
                        "AVAILABLE",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            }
        }

        return result;
    }

    public int runManualCleanup(int olderThanMinutes) {
        return orderService.cleanExpiredOrders(olderThanMinutes > 0 ? olderThanMinutes : 15);
    }

    public List<Raffle> getAllRaffles() {
        return raffleRepository.findAllByOrderByIdDesc();
    }

    @Transactional
    public Raffle updateRaffleStatus(Long id, String status) {
        Raffle raffle = raffleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Emisión no encontrada: " + id));
        raffle.setStatus(status.toUpperCase());
        return raffleRepository.save(raffle);
    }

    // Helper para parsear JSON simple de bonos [{number:"0123", desc:"$500.000 COP"}]
    private record BonusEntry(String number, String desc) {}

    private List<BonusEntry> parseSecondaryPrizes(String json) {
        List<BonusEntry> list = new ArrayList<>();
        try {
            Pattern pattern = Pattern.compile("\"number\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"desc\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(json);
            while (matcher.find()) {
                list.add(new BonusEntry(matcher.group(1), matcher.group(2)));
            }
        } catch (Exception e) {
            log.error("Error parseando secondaryPrizes JSON: {}", e.getMessage());
        }
        return list;
    }
}
