package com.adrifit.backend.payment.controller;

import com.adrifit.backend.payment.domain.PaymentStatus;
import com.adrifit.backend.payment.dto.PaymentDtos.BizumPaymentRequest;
import com.adrifit.backend.payment.dto.PaymentDtos.CardPaymentRequest;
import com.adrifit.backend.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.adrifit.backend.payment.dto.PaymentDtos.PaymentResponse;
import com.adrifit.backend.payment.dto.PaymentDtos.PaymentSummaryResponse;
import com.adrifit.backend.payment.dto.PaymentDtos.TrainerNoteRequest;
import com.adrifit.backend.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // ── Trainer ─────────────────────────────────────────────────────────────

    @GetMapping("/api/payments")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<PaymentResponse>> findAll(@RequestParam(required = false) PaymentStatus status,
                                                         @RequestParam(required = false) Long clientId) {
        return ResponseEntity.ok(paymentService.findAll(status, clientId));
    }

    @GetMapping("/api/payments/summary")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<PaymentSummaryResponse> summary() {
        return ResponseEntity.ok(paymentService.summary());
    }

    @PostMapping("/api/payments")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(request));
    }

    @PostMapping("/api/payments/{id}/cash")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<PaymentResponse> markCashPaid(@PathVariable Long id,
                                                        @Valid @RequestBody(required = false) TrainerNoteRequest request) {
        return ResponseEntity.ok(paymentService.markCashPaid(id, request != null ? request.notes() : null));
    }

    @PostMapping("/api/payments/{id}/cancel")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<PaymentResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.cancel(id));
    }

    @PostMapping("/api/payments/{id}/refund")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<PaymentResponse> refund(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.refund(id));
    }

    @GetMapping("/api/clients/{clientId}/payments")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<PaymentResponse>> findForClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(paymentService.findForClient(clientId));
    }

    // ── Client ──────────────────────────────────────────────────────────────

    @GetMapping("/api/payments/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<PaymentResponse>> findMine() {
        return ResponseEntity.ok(paymentService.findMine());
    }

    @PostMapping("/api/payments/me/{id}/card")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<PaymentResponse> payWithCard(@PathVariable Long id,
                                                       @Valid @RequestBody CardPaymentRequest request) {
        return ResponseEntity.ok(paymentService.payMineWithCard(id, request));
    }

    @PostMapping("/api/payments/me/{id}/bizum")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<PaymentResponse> payWithBizum(@PathVariable Long id,
                                                        @Valid @RequestBody BizumPaymentRequest request) {
        return ResponseEntity.ok(paymentService.payMineWithBizum(id, request));
    }
}
