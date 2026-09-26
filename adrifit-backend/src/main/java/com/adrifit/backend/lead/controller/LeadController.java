package com.adrifit.backend.lead.controller;

import com.adrifit.backend.lead.domain.LeadStatus;
import com.adrifit.backend.lead.dto.LeadDtos.ApproveRequest;
import com.adrifit.backend.lead.dto.LeadDtos.DecisionRequest;
import com.adrifit.backend.lead.dto.LeadDtos.LeadCounts;
import com.adrifit.backend.lead.dto.LeadDtos.LeadDetail;
import com.adrifit.backend.lead.dto.LeadDtos.LeadSummary;
import com.adrifit.backend.lead.dto.LeadDtos.NoteRequest;
import com.adrifit.backend.lead.dto.Questionnaire;
import com.adrifit.backend.lead.service.LeadService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trainer inbox of new client requests ("Solicitudes"). */
@RestController
@PreAuthorize("hasRole('TRAINER')")
public class LeadController {

    private final LeadService service;

    public LeadController(LeadService service) {
        this.service = service;
    }

    @GetMapping("/api/leads")
    public ResponseEntity<List<LeadSummary>> findAll(@RequestParam(value = "status", required = false) LeadStatus status) {
        return ResponseEntity.ok(service.findAll(status));
    }

    @GetMapping("/api/leads/counts")
    public ResponseEntity<LeadCounts> counts() {
        return ResponseEntity.ok(service.counts());
    }

    @GetMapping("/api/leads/{id}")
    public ResponseEntity<LeadDetail> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/api/leads/{id}/questionnaire")
    public ResponseEntity<LeadDetail> sendQuestionnaire(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return ResponseEntity.ok(service.sendQuestionnaire(id, request == null ? null : request.message()));
    }

    @PostMapping("/api/leads/{id}/reject")
    public ResponseEntity<LeadDetail> reject(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return ResponseEntity.ok(service.reject(id, request == null ? null : request.message()));
    }

    @PostMapping("/api/leads/{id}/approve")
    public ResponseEntity<LeadDetail> approve(@PathVariable Long id, @Valid @RequestBody ApproveRequest request) {
        return ResponseEntity.ok(service.approve(id, request));
    }

    @PostMapping("/api/leads/{id}/resend-activation")
    public ResponseEntity<Void> resendActivation(@PathVariable Long id) {
        service.resendActivation(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/leads/{id}/note")
    public ResponseEntity<LeadDetail> note(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(service.updateNote(id, request.note()));
    }

    @DeleteMapping("/api/leads/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Initial questionnaire of a client who came through a request (204 if none). */
    @GetMapping("/api/clients/{clientId}/questionnaire")
    public ResponseEntity<Questionnaire> questionnaireOfClient(@PathVariable Long clientId) {
        Questionnaire q = service.questionnaireOfClient(clientId);
        return q == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(q);
    }
}
