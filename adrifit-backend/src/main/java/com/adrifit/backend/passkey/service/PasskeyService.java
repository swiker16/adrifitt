package com.adrifit.backend.passkey.service;

import com.adrifit.backend.auth.dto.AuthResponse;
import com.adrifit.backend.auth.service.AuthService;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.passkey.domain.PasskeyCredential;
import com.adrifit.backend.passkey.dto.PasskeyDtos.CeremonyOptions;
import com.adrifit.backend.passkey.dto.PasskeyDtos.PasskeyResponse;
import com.adrifit.backend.passkey.repository.PasskeyCredentialRepository;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passkeys (WebAuthn): register Face ID / fingerprint / Windows Hello for the logged user, and
 * sign in without a password using a discoverable credential (no username needed).
 *
 * <p>Pending ceremonies (challenges) live in memory for 5 minutes; this is fine for a single
 * instance deployment.
 */
@Service
public class PasskeyService {

    private static final Logger log = LoggerFactory.getLogger(PasskeyService.class);
    private static final Duration CEREMONY_TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RelyingParty relyingParty;
    private final PasskeyCredentialRepository credentials;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    private final Map<String, Pending<PublicKeyCredentialCreationOptions>> registrations = new ConcurrentHashMap<>();
    private final Map<String, Pending<AssertionRequest>> assertions = new ConcurrentHashMap<>();

    private record Pending<T>(T request, Long userId, Instant expiresAt) {
    }

    public PasskeyService(RelyingParty relyingParty,
                          PasskeyCredentialRepository credentials,
                          UserRepository userRepository,
                          AuthService authService,
                          ObjectMapper objectMapper) {
        this.relyingParty = relyingParty;
        this.credentials = credentials;
        this.userRepository = userRepository;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    // ── Registration (authenticated user) ───────────────────────────────────

    @Transactional
    public CeremonyOptions startRegistration(User user) {
        purgeExpired();
        if (user.getWebauthnUserHandle() == null) {
            byte[] handle = new byte[32];
            RANDOM.nextBytes(handle);
            user.setWebauthnUserHandle(new ByteArray(handle).getBase64Url());
            userRepository.save(user);
        }
        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(StartRegistrationOptions.builder()
                .user(UserIdentity.builder()
                        .name(user.getUsername())
                        .displayName(user.getUsername())
                        .id(JpaCredentialRepository.fromBase64Url(user.getWebauthnUserHandle()))
                        .build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build())
                .timeout(120_000L)
                .build());
        String requestId = UUID.randomUUID().toString();
        registrations.put(requestId, new Pending<>(options, user.getId(), Instant.now().plus(CEREMONY_TTL)));
        try {
            return new CeremonyOptions(requestId, publicKey(options.toCredentialsCreateJson()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize WebAuthn options", e);
        }
    }

    @Transactional
    public PasskeyResponse finishRegistration(User user, String requestId, JsonNode credential, String name, String userAgent) {
        Pending<PublicKeyCredentialCreationOptions> pending = registrations.remove(requestId);
        if (pending == null || pending.expiresAt().isBefore(Instant.now()) || !pending.userId().equals(user.getId())) {
            throw new BusinessException("La solicitud ha caducado. Vuelve a intentarlo.");
        }
        RegistrationResult result;
        try {
            result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                    .request(pending.request())
                    .response(PublicKeyCredential.parseRegistrationResponseJson(objectMapper.writeValueAsString(credential)))
                    .build());
        } catch (RegistrationFailedException e) {
            log.warn("Passkey registration rejected for user {}: {}", user.getId(), e.getMessage());
            throw new BusinessException("No se pudo verificar la passkey");
        } catch (Exception e) {
            throw new BusinessException("Respuesta del dispositivo no válida");
        }
        String credentialId = result.getKeyId().getId().getBase64Url();
        if (credentials.findByCredentialId(credentialId).isPresent()) {
            throw new BusinessException("Esta passkey ya está registrada");
        }
        String transports = result.getKeyId().getTransports()
                .map(set -> set.stream().map(AuthenticatorTransport::getId).sorted().collect(Collectors.joining(",")))
                .orElse(null);
        PasskeyCredential saved = credentials.save(PasskeyCredential.builder()
                .userId(user.getId())
                .credentialId(credentialId)
                .publicKeyCose(result.getPublicKeyCose().getBase64Url())
                .signatureCount(result.getSignatureCount())
                .name(name != null && !name.isBlank() ? truncate(name.trim(), 100) : deviceName(userAgent))
                .transports(transports)
                .backupEligible(result.isBackupEligible())
                .backedUp(result.isBackedUp())
                .build());
        return toResponse(saved);
    }

    public List<PasskeyResponse> list(User user) {
        return credentials.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(User user, Long id) {
        PasskeyCredential c = credentials.findById(id)
                .filter(p -> p.getUserId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Passkey not found: " + id));
        credentials.delete(c);
    }

    // ── Sign in (public) ────────────────────────────────────────────────────

    public CeremonyOptions startAssertion() {
        purgeExpired();
        AssertionRequest request = relyingParty.startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(120_000L)
                .build());
        String requestId = UUID.randomUUID().toString();
        assertions.put(requestId, new Pending<>(request, null, Instant.now().plus(CEREMONY_TTL)));
        try {
            return new CeremonyOptions(requestId, publicKey(request.toCredentialsGetJson()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize WebAuthn options", e);
        }
    }

    @Transactional
    public AuthResponse finishAssertion(String requestId, JsonNode credential) {
        Pending<AssertionRequest> pending = assertions.remove(requestId);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("expired");
        }
        AssertionResult result;
        try {
            result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(pending.request())
                    .response(PublicKeyCredential.parseAssertionResponseJson(objectMapper.writeValueAsString(credential)))
                    .build());
        } catch (AssertionFailedException e) {
            log.warn("Passkey sign-in rejected: {}", e.getMessage());
            throw new BadCredentialsException("passkey");
        } catch (Exception e) {
            throw new BadCredentialsException("passkey");
        }
        if (!result.isSuccess()) {
            throw new BadCredentialsException("passkey");
        }
        PasskeyCredential stored = credentials.findByCredentialId(result.getCredentialId().getBase64Url())
                .orElseThrow(() -> new BadCredentialsException("passkey"));
        stored.setSignatureCount(result.getSignatureCount());
        stored.setBackedUp(result.isBackedUp());
        stored.setLastUsedAt(Instant.now());
        credentials.save(stored);
        User user = userRepository.findById(stored.getUserId()).orElseThrow(() -> new BadCredentialsException("passkey"));
        return authService.issueToken(user);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private JsonNode publicKey(String credentialsJson) throws Exception {
        return objectMapper.readTree(credentialsJson).get("publicKey");
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        registrations.values().removeIf(p -> p.expiresAt().isBefore(now));
        assertions.values().removeIf(p -> p.expiresAt().isBefore(now));
    }

    private PasskeyResponse toResponse(PasskeyCredential c) {
        return new PasskeyResponse(c.getId(), c.getName(), c.isBackedUp(), c.getCreatedAt(), c.getLastUsedAt());
    }

    /** "iPhone · Safari", "Android · Chrome", "Windows · Edge"... */
    static String deviceName(String ua) {
        if (ua == null) return "Mi dispositivo";
        String device = ua.contains("iPhone") ? "iPhone" : ua.contains("iPad") ? "iPad"
                : ua.contains("Android") ? "Android" : ua.contains("Mac OS X") ? "Mac"
                : ua.contains("Windows") ? "Windows" : ua.contains("Linux") ? "Linux" : "Dispositivo";
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("SamsungBrowser") ? "Samsung Internet"
                : ua.contains("Firefox/") ? "Firefox" : ua.contains("Chrome/") || ua.contains("CriOS") ? "Chrome"
                : ua.contains("Safari/") ? "Safari" : null;
        return browser != null ? device + " · " + browser : device;
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
