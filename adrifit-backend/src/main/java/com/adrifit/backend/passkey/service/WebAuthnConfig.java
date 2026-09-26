package com.adrifit.backend.passkey.service;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WebAuthn relying party. In production set WEBAUTHN_RP_ID to the domain (e.g. app.adrifit.com)
 * and WEBAUTHN_ORIGINS to the exact https origin(s) of the frontend.
 */
@Configuration
public class WebAuthnConfig {

    @Bean
    public RelyingParty relyingParty(JpaCredentialRepository credentialRepository,
                                     @Value("${adrifit.webauthn.rp-id:localhost}") String rpId,
                                     @Value("${adrifit.webauthn.rp-name:AdriFitt}") String rpName,
                                     @Value("${adrifit.webauthn.origins:http://localhost:4200,http://localhost:4300}") String origins) {
        Set<String> allowed = Arrays.stream(origins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id(rpId).name(rpName).build())
                .credentialRepository(credentialRepository)
                .origins(allowed)
                // Local development runs on several ports of localhost.
                .allowOriginPort("localhost".equals(rpId))
                .build();
    }
}
