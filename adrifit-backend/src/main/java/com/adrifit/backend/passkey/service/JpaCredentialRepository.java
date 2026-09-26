package com.adrifit.backend.passkey.service;

import com.adrifit.backend.passkey.domain.PasskeyCredential;
import com.adrifit.backend.passkey.repository.PasskeyCredentialRepository;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.exception.Base64UrlException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Bridges the Yubico relying party with our tables (users + passkey_credentials). */
@Component
public class JpaCredentialRepository implements CredentialRepository {

    private final UserRepository userRepository;
    private final PasskeyCredentialRepository credentials;

    public JpaCredentialRepository(UserRepository userRepository, PasskeyCredentialRepository credentials) {
        this.userRepository = userRepository;
        this.credentials = credentials;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return userRepository.findByUsername(username)
                .map(u -> credentials.findByUserIdOrderByCreatedAtDesc(u.getId()).stream()
                        .map(this::descriptor)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userRepository.findByUsername(username)
                .map(User::getWebauthnUserHandle)
                .map(JpaCredentialRepository::fromBase64Url);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return userRepository.findByWebauthnUserHandle(userHandle.getBase64Url()).map(User::getUsername);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return credentials.findByCredentialId(credentialId.getBase64Url())
                .flatMap(c -> userRepository.findById(c.getUserId())
                        .filter(u -> userHandle.getBase64Url().equals(u.getWebauthnUserHandle()))
                        .map(u -> registered(c, u)));
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return credentials.findByCredentialId(credentialId.getBase64Url())
                .flatMap(c -> userRepository.findById(c.getUserId()).map(u -> registered(c, u)))
                .map(Set::of)
                .orElse(Set.of());
    }

    private RegisteredCredential registered(PasskeyCredential c, User u) {
        return RegisteredCredential.builder()
                .credentialId(fromBase64Url(c.getCredentialId()))
                .userHandle(fromBase64Url(u.getWebauthnUserHandle()))
                .publicKeyCose(fromBase64Url(c.getPublicKeyCose()))
                .signatureCount(c.getSignatureCount())
                .backupEligible(c.isBackupEligible())
                .backupState(c.isBackedUp())
                .build();
    }

    private PublicKeyCredentialDescriptor descriptor(PasskeyCredential c) {
        PublicKeyCredentialDescriptor.PublicKeyCredentialDescriptorBuilder builder =
                PublicKeyCredentialDescriptor.builder().id(fromBase64Url(c.getCredentialId()));
        if (c.getTransports() != null && !c.getTransports().isBlank()) {
            builder.transports(Arrays.stream(c.getTransports().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(AuthenticatorTransport::of)
                    .collect(Collectors.toSet()));
        }
        return builder.build();
    }

    static ByteArray fromBase64Url(String value) {
        try {
            return ByteArray.fromBase64Url(value);
        } catch (Base64UrlException e) {
            throw new IllegalStateException("Invalid base64url value in passkey storage", e);
        }
    }
}
