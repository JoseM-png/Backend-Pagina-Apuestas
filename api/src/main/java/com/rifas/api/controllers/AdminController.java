package com.rifas.api.controllers;

import com.rifas.api.dtos.*;
import com.rifas.api.models.Raffle;
import com.rifas.api.services.AdminService;
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
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final com.rifas.api.services.PaymentService paymentService;

    @PostMapping("/auth/login")
    public ResponseEntity<AdminLoginResponse> login(@RequestBody AdminLoginRequest request) {
        AdminLoginResponse response = adminService.login(request);
        if (response.success()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<AdminStatsResponse> getDashboardStats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/orders")
    public ResponseEntity<Page<AdminOrderDTO>> getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        return ResponseEntity.ok(adminService.getOrders(status, search, page, size));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrderDetails(@PathVariable String id) {
        try {
            return ResponseEntity.ok(adminService.getOrderDetails(id));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/orders/{id}/approve")
    public ResponseEntity<?> approveOrder(@PathVariable String id) {
        try {
            adminService.approveOrder(id);
            return ResponseEntity.ok(Map.of("message", "Orden aprobada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/orders/{id}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable String id) {
        try {
            adminService.cancelOrder(id);
            return ResponseEntity.ok(Map.of("message", "Orden cancelada y números liberados"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/payments")
    public ResponseEntity<?> getPayments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        return ResponseEntity.ok(paymentService.searchPayments(status, search, page, size));
    }

    @GetMapping("/payments/stats")
    public ResponseEntity<?> getPaymentStats() {
        return ResponseEntity.ok(paymentService.getPaymentStats());
    }

    @GetMapping("/bonuses/status")
    public ResponseEntity<List<BonusWinnerDTO>> getBonusesStatus() {
        return ResponseEntity.ok(adminService.getBonusesStatus());
    }

    @PostMapping("/maintenance/clean-expired")
    public ResponseEntity<?> cleanExpired(@RequestParam(defaultValue = "15") int olderThanMinutes) {
        int cleaned = adminService.runManualCleanup(olderThanMinutes);
        return ResponseEntity.ok(Map.of(
                "message", "Limpieza ejecutada exitosamente",
                "cleanedOrders", cleaned
        ));
    }

    @GetMapping("/raffles")
    public ResponseEntity<List<Raffle>> getAllRaffles() {
        return ResponseEntity.ok(adminService.getAllRaffles());
    }

    @PatchMapping("/raffles/{id}/status")
    public ResponseEntity<?> updateRaffleStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload
    ) {
        try {
            String status = payload.get("status");
            if (status == null || status.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El estado es requerido"));
            }
            Raffle updated = adminService.updateRaffleStatus(id, status);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
