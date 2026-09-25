package com.adrifit.backend.email.controller;

import com.adrifit.backend.email.dto.EmailResponse;
import com.adrifit.backend.email.dto.SendEmailRequest;
import com.adrifit.backend.email.service.EmailService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emails")
@PreAuthorize("hasRole('TRAINER')")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping
    public ResponseEntity<List<EmailResponse>> findAll(@RequestParam(required = false) Long clientId) {
        return ResponseEntity.ok(emailService.findAll(clientId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(emailService.findById(id));
    }

    @PostMapping
    public ResponseEntity<List<EmailResponse>> send(@Valid @RequestBody SendEmailRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(emailService.sendCustom(request));
    }
}
