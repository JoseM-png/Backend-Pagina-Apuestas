package com.rifas.api.controllers;

import com.rifas.api.dtos.PaymentDTO;
import com.rifas.api.dtos.PaymentIntentRequest;
import com.rifas.api.dtos.PaymentResultRequest;
import com.rifas.api.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/intent")
    public ResponseEntity<PaymentDTO> createPaymentIntent(@RequestBody PaymentIntentRequest request) {
        try {
            PaymentDTO payment = paymentService.recordPaymentIntent(request);
            return new ResponseEntity<>(payment, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Error creando intención de pago: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/wompi-result")
    public ResponseEntity<PaymentDTO> processWompiResult(@RequestBody PaymentResultRequest request) {
        try {
            PaymentDTO payment = paymentService.processWompiResult(request);
            return ResponseEntity.ok(payment);
        } catch (Exception e) {
            log.error("Error registrando resultado de Wompi: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<PaymentDTO>> getPayments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        return ResponseEntity.ok(paymentService.searchPayments(status, search, page, size));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentDTO>> getPaymentsByOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(paymentService.getPaymentsByOrder(orderId));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getPaymentStats() {
        return ResponseEntity.ok(paymentService.getPaymentStats());
    }
}
