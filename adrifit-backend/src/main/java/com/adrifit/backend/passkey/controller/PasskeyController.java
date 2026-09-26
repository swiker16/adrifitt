package com.adrifit.backend.passkey.controller;

import com.adrifit.backend.auth.dto.AuthResponse;
import com.adrifit.backend.passkey.dto.PasskeyDtos.CeremonyOptions;
import com.adrifit.backend.passkey.dto.PasskeyDtos.FinishAssertionRequest;
import com.adrifit.backend.passkey.dto.PasskeyDtos.FinishRegistrationRequest;
import com.adrifit.backend.passkey.dto.PasskeyDtos.PasskeyResponse;
import com.adrifit.backend.passkey.service.PasskeyService;
import com.adrifit.backend.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasskeyController {

    private final PasskeyService passkeyService;
    private final UserService userService;

    public PasskeyController(PasskeyService passkeyService, UserService userService) {
        this.passkeyService = passkeyService;
        this.userService = userService;
    }

    // ── Manage my passkeys (authenticated) ──────────────────────────────────

    @GetMapping("/api/passkeys")
    public ResponseEntity<List<PasskeyResponse>> list() {
        return ResponseEntity.ok(passkeyService.list(userService.getCurrentUser()));
    }

    @PostMapping("/api/passkeys/register/options")
    public ResponseEntity<CeremonyOptions> registrationOptions() {
        return ResponseEntity.ok(passkeyService.startRegistration(userService.getCurrentUser()));
    }

    @PostMapping("/api/passkeys/register/finish")
    public ResponseEntity<PasskeyResponse> finishRegistration(
            @Valid @RequestBody FinishRegistrationRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return ResponseEntity.status(HttpStatus.CREATED).body(passkeyService.finishRegistration(
                userService.getCurrentUser(), request.requestId(), request.credential(), request.name(), userAgent));
    }

    @DeleteMapping("/api/passkeys/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        passkeyService.delete(userService.getCurrentUser(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Sign in with a passkey (public) ─────────────────────────────────────

    @PostMapping("/api/auth/passkey/options")
    public ResponseEntity<CeremonyOptions> assertionOptions() {
        return ResponseEntity.ok(passkeyService.startAssertion());
    }

    @PostMapping("/api/auth/passkey/finish")
    public ResponseEntity<AuthResponse> finishAssertion(@Valid @RequestBody FinishAssertionRequest request) {
        return ResponseEntity.ok(passkeyService.finishAssertion(request.requestId(), request.credential()));
    }
}
