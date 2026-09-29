package com.rifas.api.controllers;

import com.rifas.api.dtos.PurchaseRequest;
import com.rifas.api.models.Order;
import com.rifas.api.services.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final com.rifas.api.services.PaymentService paymentService;

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody PurchaseRequest request) {
        try {
            Order order = orderService.createOrder(request);
            return new ResponseEntity<>(order, HttpStatus.CREATED);
        } catch (RuntimeException e) {
            log.error("Error al procesar checkout: {}", e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/search-tickets")
    public ResponseEntity<?> searchTickets(@RequestParam String email) {
        List<String> tickets = orderService.getUserTicketsByEmail(email);
        if (tickets.isEmpty()) {
            return new ResponseEntity<>("No se encontraron números para este correo.", HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(tickets, HttpStatus.OK);
    }

    @GetMapping("/{orderId}/tickets")
    public ResponseEntity<?> getOrderTickets(@PathVariable String orderId) {
        List<String> tickets = orderService.getTicketsByOrderId(orderId);
        return new ResponseEntity<>(tickets, HttpStatus.OK);
    }

    @PostMapping("/webhook/confirm/{orderId}")
    public ResponseEntity<?> confirmPaymentWebhook(@PathVariable String orderId) {
        try {
            String result = orderService.confirmPayment(orderId);
            // Registrar pago manual/aprobado si no existe
            paymentService.recordPaymentIntent(new com.rifas.api.dtos.PaymentIntentRequest(
                    orderId,
                    "MANUAL_CONFIRM_" + orderId,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    // Webhook de Wompi robusto con persistencia en tabla de pagos y actualización de orden
    @PostMapping("/webhook")
    public ResponseEntity<String> wompiWebhook(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Recibiendo webhook de Wompi...");
            paymentService.processWompiWebhook(payload);
            return ResponseEntity.ok("Webhook de Wompi procesado y registrado correctamente");
        } catch (Exception e) {
            log.error("Error procesando el webhook de Wompi: {}", e.getMessage(), e);
            return ResponseEntity.ok("Error interno procesando webhook, pero recibido");
        }
    }

    private String extractOrderIdFromReference(String reference) {
        // Formatos soportados:
        // 1. "JR_ORDEN_uuid_timestamp"
        // 2. "JR-ORDEN-uuid"
        // 3. "uuid"
        if (reference.contains("_")) {
            String[] parts = reference.split("_");
            if (parts.length >= 3) {
                return parts[2]; // JR_ORDEN_{UUID}_{TIMESTAMP} -> parts[2]
            }
        }
        if (reference.startsWith("JR-ORDEN-")) {
            String remainder = reference.substring("JR-ORDEN-".length());
            int lastDash = remainder.lastIndexOf('-');
            // Si el último fragmento es un timestamp numérico (ej. 1712345678901)
            if (lastDash > 0 && remainder.substring(lastDash + 1).matches("\\d+")) {
                return remainder.substring(0, lastDash);
            }
            return remainder;
        }
        return reference;
    }
}