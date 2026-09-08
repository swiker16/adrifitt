package com.adrifit.backend.client.controller;

import com.adrifit.backend.client.dto.ClientCreatedResponse;
import com.adrifit.backend.client.dto.ClientResponse;
import com.adrifit.backend.client.dto.CreateClientRequest;
import com.adrifit.backend.client.dto.UpdateClientRequest;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.subscription.dto.AssignPlanRequest;
import com.adrifit.backend.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/clients")
@PreAuthorize("hasRole('TRAINER')")
public class ClientController {

    private final ClientService clientService;
    private final SubscriptionService subscriptionService;

    public ClientController(ClientService clientService, SubscriptionService subscriptionService) {
        this.clientService = clientService;
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<ClientCreatedResponse> create(@Valid @RequestBody CreateClientRequest request) {
        ClientCreatedResponse created = clientService.create(request);
        subscriptionService.assignPlan(created.client().id(), new AssignPlanRequest(request.planId()));
        return ResponseEntity.created(URI.create("/api/clients/" + created.client().id())).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ClientResponse>> findAll() {
        return ResponseEntity.ok(clientService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody UpdateClientRequest request) {
        return ResponseEntity.ok(clientService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClientResponse> uploadPhoto(@PathVariable Long id,
                                                      @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(clientService.uploadPhoto(id, file));
    }
}
