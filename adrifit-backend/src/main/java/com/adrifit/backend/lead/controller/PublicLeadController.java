package com.adrifit.backend.lead.controller;

import com.adrifit.backend.auth.dto.AuthResponse;
import com.adrifit.backend.auth.service.AccountActivationService;
import com.adrifit.backend.lead.dto.LeadDtos.ActivateRequest;
import com.adrifit.backend.lead.dto.LeadDtos.ActivationInfo;
import com.adrifit.backend.lead.dto.LeadDtos.ContactRequest;
import com.adrifit.backend.lead.dto.LeadDtos.QuestionnaireInfo;
import com.adrifit.backend.lead.dto.Questionnaire;
import com.adrifit.backend.lead.service.LeadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for people who are not clients yet (no login): contact, questionnaire, activation. */
@RestController
@RequestMapping("/api/public")
public class PublicLeadController {

    private final LeadService leadService;
    private final AccountActivationService activationService;

    public PublicLeadController(LeadService leadService, AccountActivationService activationService) {
        this.leadService = leadService;
        this.activationService = activationService;
    }

    @PostMapping("/leads")
    public ResponseEntity<Map<String, String>> contact(@Valid @RequestBody ContactRequest request, HttpServletRequest http) {
        leadService.submitContact(request, clientIp(http));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "received"));
    }

    @GetMapping("/questionnaire/{token}")
    public ResponseEntity<QuestionnaireInfo> questionnaire(@PathVariable String token) {
        return ResponseEntity.ok(leadService.questionnaireInfo(token));
    }

    @PostMapping("/questionnaire/{token}")
    public ResponseEntity<Void> submitQuestionnaire(@PathVariable String token, @Valid @RequestBody Questionnaire answers) {
        leadService.submitQuestionnaire(token, answers);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/activation/{token}")
    public ResponseEntity<ActivationInfo> activation(@PathVariable String token) {
        return ResponseEntity.ok(activationService.info(token));
    }

    /** Sets the password and signs the new client in. */
    @PostMapping("/activation/{token}")
    public ResponseEntity<AuthResponse> activate(@PathVariable String token, @Valid @RequestBody ActivateRequest request) {
        return ResponseEntity.ok(activationService.activate(token, request.password()));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
