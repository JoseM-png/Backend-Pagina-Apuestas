package com.rifas.api.services;

import com.rifas.api.dtos.PaymentDTO;
import com.rifas.api.dtos.PaymentIntentRequest;
import com.rifas.api.dtos.PaymentResultRequest;
import com.rifas.api.models.Order;
import com.rifas.api.models.Payment;
import com.rifas.api.repositories.OrderRepository;
import com.rifas.api.repositories.PaymentRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Transactional
    public PaymentDTO recordPaymentIntent(PaymentIntentRequest request) {
        log.info("Iniciando intención de pago para referencia: {}", request.reference());

        Order order = null;
        if (request.orderId() != null && !request.orderId().isBlank()) {
            order = orderRepository.findById(request.orderId()).orElse(null);
        }

        // Buscar si ya existe un registro para esta referencia
        Payment payment = paymentRepository.findByReference(request.reference())
                .orElse(new Payment());

        payment.setReference(request.reference());
        payment.setOrder(order);
        payment.setAmountInCents(request.amountInCents());
        payment.setAmount(request.amount() != null ? request.amount() : (request.amountInCents() != null ? request.amountInCents() / 100.0 : 0.0));
        payment.setCurrency("COP");
        payment.setStatus("PENDING");
        payment.setCustomerEmail(request.customerEmail());
        payment.setCustomerName(request.customerName());
        payment.setCustomerPhone(request.customerPhone());
        payment.setGateway("WOMPI");
        payment.setPaymentDate(LocalDateTime.now());

        Payment saved = paymentRepository.save(payment);
        return toDTO(saved);
    }

    @Transactional
    public PaymentDTO processWompiResult(PaymentResultRequest request) {
        log.info("Procesando resultado de Wompi. Ref: {}, Transacción: {}, Estado: {}", 
                request.reference(), request.transactionId(), request.status());

        // 1. Encontrar o crear pago
        Payment payment = null;
        if (request.transactionId() != null && !request.transactionId().isBlank()) {
            payment = paymentRepository.findByTransactionId(request.transactionId()).orElse(null);
        }
        if (payment == null && request.reference() != null && !request.reference().isBlank()) {
            payment = paymentRepository.findByReference(request.reference()).orElse(null);
        }
        if (payment == null) {
            payment = new Payment();
        }

        // 2. Asociar orden
        Order order = payment.getOrder();
        String orderId = request.orderId();
        if (order == null && (orderId == null || orderId.isBlank()) && request.reference() != null) {
            orderId = extractOrderIdFromReference(request.reference());
        }
        if (order == null && orderId != null && !orderId.isBlank()) {
            order = orderRepository.findById(orderId).orElse(null);
        }
        if (order != null) {
            payment.setOrder(order);
            if (payment.getCustomerEmail() == null) payment.setCustomerEmail(order.getCustomerEmail());
            if (payment.getCustomerName() == null) payment.setCustomerName(order.getCustomerName());
            if (payment.getCustomerPhone() == null) payment.setCustomerPhone(order.getCustomerPhone());
        }

        if (request.customerEmail() != null) payment.setCustomerEmail(request.customerEmail());
        if (request.customerName() != null) payment.setCustomerName(request.customerName());
        if (request.customerPhone() != null) payment.setCustomerPhone(request.customerPhone());

        payment.setTransactionId(request.transactionId());
        if (request.reference() != null) payment.setReference(request.reference());
        payment.setStatus(request.status() != null ? request.status().toUpperCase() : "PENDING");
        payment.setStatusMessage(request.statusMessage());
        payment.setGateway("WOMPI");

        if (request.amountInCents() != null) {
            payment.setAmountInCents(request.amountInCents());
            payment.setAmount(request.amountInCents() / 100.0);
        } else if (request.amount() != null) {
            payment.setAmount(request.amount());
            payment.setAmountInCents(Math.round(request.amount() * 100));
        }

        if (request.currency() != null) payment.setCurrency(request.currency());
        if (request.paymentMethodType() != null) payment.setPaymentMethodType(request.paymentMethodType());
        if (request.rawPayload() != null) payment.setRawPayload(request.rawPayload());
        payment.setPaymentDate(LocalDateTime.now());

        Payment saved = paymentRepository.save(payment);

        // 3. Sincronizar estado de la Orden
        if (order != null) {
            String normStatus = payment.getStatus();
            if ("APPROVED".equals(normStatus)) {
                order.setGatewayTransactionId(payment.getTransactionId());
                order.setPaymentStatus("COMPLETED");
                orderRepository.save(order);
                log.info("¡Orden #{} COMPLETADA tras confirmación de pago!", order.getId());
            } else if ("DECLINED".equals(normStatus) || "ERROR".equals(normStatus) || "VOIDED".equals(normStatus)) {
                orderService.updatePaymentStatus(order.getId(), "FAILED");
                log.warn("Orden #{} marcada como FAILED debido a pago {}", order.getId(), normStatus);
            }
        }

        return toDTO(saved);
    }

    @Transactional
    public PaymentDTO processWompiWebhook(Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            if (data == null) {
                log.warn("Webhook sin campo 'data'");
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> transaction = (Map<String, Object>) data.get("transaction");
            if (transaction == null) {
                log.warn("Webhook sin campo 'transaction'");
                return null;
            }

            String transactionId = (String) transaction.get("id");
            String status = (String) transaction.get("status");
            String statusMessage = (String) transaction.get("status_message");
            String reference = (String) transaction.get("reference");
            String currency = (String) transaction.get("currency");
            String paymentMethodType = (String) transaction.get("payment_method_type");

            @SuppressWarnings("unchecked")
            Map<String, Object> paymentMethod = (Map<String, Object>) transaction.get("payment_method");
            if (paymentMethodType == null && paymentMethod != null) {
                paymentMethodType = (String) paymentMethod.get("type");
            }

            Long amountInCents = null;
            Object amountObj = transaction.get("amount_in_cents");
            if (amountObj instanceof Number num) {
                amountInCents = num.longValue();
            }

            String customerEmail = (String) transaction.get("customer_email");
            @SuppressWarnings("unchecked")
            Map<String, Object> customerData = (Map<String, Object>) transaction.get("customer_data");
            String customerName = null;
            String customerPhone = null;
            if (customerData != null) {
                customerName = (String) customerData.get("full_name");
                customerPhone = (String) customerData.get("phone_number");
            }

            String rawJson = payload != null ? payload.toString() : "";

            String orderId = extractOrderIdFromReference(reference);

            PaymentResultRequest req = new PaymentResultRequest(
                    orderId,
                    transactionId,
                    reference,
                    status,
                    statusMessage,
                    amountInCents,
                    amountInCents != null ? amountInCents / 100.0 : null,
                    currency,
                    paymentMethodType,
                    customerEmail,
                    customerName,
                    customerPhone,
                    rawJson
            );

            return processWompiResult(req);

        } catch (Exception e) {
            log.error("Error al procesar webhook de Wompi en PaymentService: {}", e.getMessage(), e);
            throw new RuntimeException("Error procesando pago de Wompi", e);
        }
    }

    public Page<PaymentDTO> searchPayments(String status, String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by("createdAt").descending());
        String cleanStatus = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) ? status.trim().toUpperCase() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<Payment> paymentPage = paymentRepository.searchPayments(cleanStatus, cleanSearch, pageable);
        return paymentPage.map(this::toDTO);
    }

    public List<PaymentDTO> getPaymentsByOrder(String orderId) {
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public Map<String, Object> getPaymentStats() {
        long totalPayments = paymentRepository.count();
        long approvedCount = paymentRepository.countByStatus("APPROVED");
        long pendingCount = paymentRepository.countByStatus("PENDING");
        long declinedCount = paymentRepository.countByStatus("DECLINED");
        long errorCount = paymentRepository.countByStatus("ERROR");
        Double totalApprovedAmount = paymentRepository.sumApprovedPaymentsAmount();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPayments", totalPayments);
        stats.put("approvedCount", approvedCount);
        stats.put("pendingCount", pendingCount);
        stats.put("declinedCount", declinedCount + errorCount);
        stats.put("totalApprovedAmount", totalApprovedAmount != null ? totalApprovedAmount : 0.0);
        return stats;
    }

    public String extractOrderIdFromReference(String reference) {
        if (reference == null || reference.isBlank()) return null;
        // 1. "JR_ORDEN_uuid_timestamp"
        if (reference.contains("_")) {
            String[] parts = reference.split("_");
            if (parts.length >= 3) {
                return parts[2];
            }
        }
        // 2. "JR-ORDEN-uuid-timestamp"
        if (reference.startsWith("JR-ORDEN-")) {
            String remainder = reference.substring("JR-ORDEN-".length());
            int lastDash = remainder.lastIndexOf('-');
            if (lastDash > 0 && remainder.substring(lastDash + 1).matches("\\d+")) {
                return remainder.substring(0, lastDash);
            }
            return remainder;
        }
        return reference;
    }

    private PaymentDTO toDTO(Payment payment) {
        return new PaymentDTO(
                payment.getId(),
                payment.getOrder() != null ? payment.getOrder().getId() : null,
                payment.getTransactionId(),
                payment.getReference(),
                payment.getAmountInCents(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethodType(),
                payment.getStatus(),
                payment.getStatusMessage(),
                payment.getCustomerEmail(),
                payment.getCustomerName(),
                payment.getCustomerPhone(),
                payment.getGateway(),
                payment.getPaymentDate(),
                payment.getCreatedAt(),
                payment.getRawPayload()
        );
    }
}
