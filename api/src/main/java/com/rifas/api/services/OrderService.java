package com.rifas.api.services;

import com.rifas.api.dtos.PurchaseRequest;
import com.rifas.api.models.Order;
import com.rifas.api.models.Raffle;
import com.rifas.api.models.Ticket;
import com.rifas.api.repositories.OrderRepository;
import com.rifas.api.repositories.RaffleRepository;
import com.rifas.api.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final RaffleRepository raffleRepository;

    @Transactional
    public Order createOrder(PurchaseRequest request) {

        // 1. Buscamos la rifa
        Raffle raffle = raffleRepository.findById(request.raffleId())
                .orElseThrow(() -> new RuntimeException("Rifa no encontrada"));

        int totalTickets = raffle.getTotalTickets();
        int digits = String.valueOf(totalTickets - 1).length();
        int requestedAmount = request.amountOfTickets();

        List<String> assignedNumbers;

        try {
            // Intentamos generar los números con la consulta nativa optimizada en PostgreSQL
            assignedNumbers = ticketRepository.findRandomAvailableTicketNumbers(
                    raffle.getId(),
                    totalTickets,
                    digits,
                    requestedAmount
            );
        } catch (Exception ex) {
            log.warn("Fallo al ejecutar findRandomAvailableTicketNumbers nativo, recurriendo a generador en memoria: {}", ex.getMessage());
            assignedNumbers = Collections.emptyList();
        }

        // Si la consulta nativa no devolvió suficientes (o fallo por compatibilidad)
        if (assignedNumbers == null || assignedNumbers.size() < requestedAmount) {
            List<String> takenNumbersList = ticketRepository.findTakenTicketNumbers(raffle.getId());
            Set<String> takenNumbers = new HashSet<>(takenNumbersList);

            if (totalTickets - takenNumbers.size() < requestedAmount) {
                throw new RuntimeException("No hay suficientes credenciales disponibles en esta emisión.");
            }

            Set<String> fallbackSet = new HashSet<>();
            Random random = new Random();
            String format = "%0" + digits + "d";

            int maxAttempts = requestedAmount * 50;
            int attempts = 0;
            while (fallbackSet.size() < requestedAmount && attempts < maxAttempts) {
                attempts++;
                int randomNum = random.nextInt(totalTickets);
                String ticketStr = String.format(format, randomNum);
                if (!takenNumbers.contains(ticketStr) && !fallbackSet.contains(ticketStr)) {
                    fallbackSet.add(ticketStr);
                }
            }

            if (fallbackSet.size() < requestedAmount) {
                throw new RuntimeException("No se pudieron asignar los números solicitados en este momento. Inténtalo de nuevo.");
            }
            assignedNumbers = new ArrayList<>(fallbackSet);
        }

        // 2. Creamos la Orden
        Order newOrder = new Order();
        newOrder.setCustomerName(request.customerName().trim());
        newOrder.setCustomerEmail(request.customerEmail().trim().toLowerCase());
        newOrder.setCustomerPhone(request.customerPhone().trim());
        newOrder.setCustomerAddress(request.customerAddress() != null ? request.customerAddress().trim() : "");
        newOrder.setTotalAmount(requestedAmount * raffle.getTicketPrice());
        newOrder.setPaymentStatus("PENDING");
        Order savedOrder = orderRepository.save(newOrder);

        // 3. Guardamos los Tickets en la base de datos
        List<Ticket> ticketsToSave = new ArrayList<>(assignedNumbers.size());
        for (String ticketNumber : assignedNumbers) {
            Ticket ticket = new Ticket();
            ticket.setTicketNumber(ticketNumber);
            ticket.setRaffle(raffle);
            ticket.setOrder(savedOrder);
            ticketsToSave.add(ticket);
        }

        ticketRepository.saveAll(ticketsToSave);

        return savedOrder;
    }

    // 1. Método para buscar los tickets por correo
    public List<String> getUserTicketsByEmail(String email) {
        return ticketRepository.findByOrderCustomerEmail(email.trim().toLowerCase())
                .stream()
                .map(Ticket::getTicketNumber)
                .toList();
    }

    public List<String> getTicketsByOrderId(String orderId) {
        return ticketRepository.findByOrderId(orderId)
                .stream()
                .map(Ticket::getTicketNumber)
                .toList();
    }

    // 2. Método para confirmar el pago (Lo llamará la pasarela de pagos)
    @Transactional
    public String confirmPayment(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        if ("COMPLETED".equals(order.getPaymentStatus())) {
            return "El pago ya había sido procesado";
        }

        order.setPaymentStatus("COMPLETED");
        orderRepository.save(order);

        return "Pago confirmado exitosamente. Números asegurados.";
    }

    @Transactional
    public void updatePaymentStatus(String orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada para el ID: " + orderId));

        order.setPaymentStatus(status);
        orderRepository.save(order);

        // Si la pasarela marca el pago como FAILED, liberamos los tickets automáticamente
        if ("FAILED".equals(status)) {
            ticketRepository.deleteByOrderId(orderId);
            log.info("Tickets liberados para la orden fallida {}", orderId);
        }
    }

    @Transactional
    public void cancelOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada: " + orderId));

        ticketRepository.deleteByOrderId(orderId);
        order.setPaymentStatus("FAILED");
        orderRepository.save(order);
        log.info("Orden {} cancelada manualmente y tickets liberados.", orderId);
    }

    @Transactional
    public void approveOrderManually(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada: " + orderId));

        order.setPaymentStatus("COMPLETED");
        orderRepository.save(order);
        log.info("Orden {} aprobada manualmente por el administrador.", orderId);
    }

    // ⚡ Tarea programada: Se ejecuta cada 15 minutos para liberar órdenes PENDING expiradas
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void scheduledCleanExpiredPendingOrders() {
        cleanExpiredOrders(15);
    }

    @Transactional
    public int cleanExpiredOrders(int olderThanMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(olderThanMinutes);
        List<Order> expiredOrders = orderRepository.findByPaymentStatusAndCreatedAtBefore("PENDING", cutoff);

        if (expiredOrders.isEmpty()) {
            return 0;
        }

        log.info("Limpiando {} órdenes PENDING expiradas creadas antes de {}", expiredOrders.size(), cutoff);
        ticketRepository.deleteByOrderIn(expiredOrders);

        for (Order order : expiredOrders) {
            order.setPaymentStatus("FAILED");
        }
        orderRepository.saveAll(expiredOrders);

        return expiredOrders.size();
    }
}