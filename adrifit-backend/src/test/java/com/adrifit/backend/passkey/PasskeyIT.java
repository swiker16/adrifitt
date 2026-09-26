package com.adrifit.backend.passkey;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Server side of the passkey ceremonies. The full browser flow (Face ID / fingerprint) is
 * exercised end-to-end with a virtual authenticator in the E2E suite.
 */
class PasskeyIT extends AbstractIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void registrationOptionsRequireDiscoverableCredentialAndUserVerification() {
        createClient(trainerToken(), "ana", "ana@mail.com", "ana12345");
        String client = login("ana", "ana12345");

        ResponseEntity<Map<String, Object>> options = post("/api/passkeys/register/options", Map.of(), client);

        assertThat(options.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(options.getBody().get("requestId")).isNotNull();
        Map<String, Object> publicKey = (Map<String, Object>) options.getBody().get("publicKey");
        assertThat(((Map<String, Object>) publicKey.get("rp")).get("id")).isEqualTo("localhost");
        assertThat(((Map<String, Object>) publicKey.get("user")).get("name")).isEqualTo("ana");
        Map<String, Object> selection = (Map<String, Object>) publicKey.get("authenticatorSelection");
        assertThat(selection.get("residentKey")).isEqualTo("required");
        assertThat(selection.get("userVerification")).isEqualTo("required");
        assertThat((String) publicKey.get("challenge")).isNotBlank();
        // A stable, random user handle was created (not the username or email).
        String handle = jdbc.queryForObject("SELECT webauthn_user_handle FROM users WHERE username = 'ana'", String.class);
        assertThat(handle).isNotBlank().doesNotContain("ana");
        assertThat(get("/api/auth/me", client).getBody().get("passkeys")).isEqualTo(0);
    }

    @Test
    void invalidOrExpiredCeremoniesAreRejected() {
        createClient(trainerToken(), "ana", "ana@mail.com", "ana12345");
        String client = login("ana", "ana12345");

        assertThat(post("/api/passkeys/register/finish",
                Map.of("requestId", "nope", "credential", Map.of("id", "x")), client).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // Sign-in options are public (no token) and don't reveal any user.
        ResponseEntity<Map<String, Object>> signIn = post("/api/auth/passkey/options", Map.of(), null);
        assertThat(signIn.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> publicKey = (Map<String, Object>) signIn.getBody().get("publicKey");
        assertThat(publicKey.get("allowCredentials")).isNull();
        assertThat(publicKey.get("userVerification")).isEqualTo("required");

        String requestId = (String) signIn.getBody().get("requestId");
        Map<String, Object> garbage = Map.of("id", "AAAA", "rawId", "AAAA", "type", "public-key",
                "response", Map.of("clientDataJSON", "AAAA", "authenticatorData", "AAAA", "signature", "AAAA"),
                "clientExtensionResults", Map.of());
        assertThat(post("/api/auth/passkey/finish", Map.of("requestId", requestId, "credential", garbage), null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // The challenge is single-use.
        assertThat(post("/api/auth/passkey/finish", Map.of("requestId", requestId, "credential", garbage), null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passkeyManagementRequiresAuthentication() {
        assertThat(get("/api/passkeys", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        createClient(trainerToken(), "ana", "ana@mail.com", "ana12345");
        List<Map<String, Object>> mine = getList("/api/passkeys", login("ana", "ana12345")).getBody();
        assertThat(mine).isEmpty();
    }
}
