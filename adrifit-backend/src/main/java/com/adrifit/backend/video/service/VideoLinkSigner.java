package com.adrifit.backend.video.service;

import com.adrifit.backend.common.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Signs short-lived video URLs. A {@code <video>} element cannot send the Authorization
 * header, and the long-lived JWT must not travel in URLs (logs, history), so the API hands
 * out an HMAC-signed link that only opens one video for a limited time.
 */
@Component
public class VideoLinkSigner {

    static final Duration TTL = Duration.ofHours(3);

    private final SecretKeySpec key;

    public VideoLinkSigner(JwtProperties jwt) {
        // Derived from the JWT secret with a purpose prefix: stable across restarts, never equal to it.
        this.key = new SecretKeySpec(("video-link:" + jwt.getSecret()).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public Instant expiry() {
        return Instant.now().plus(TTL);
    }

    public String sign(long videoId, long expiresEpochSecond) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] sig = mac.doFinal((videoId + ":" + expiresEpochSecond).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isValid(long videoId, long expiresEpochSecond, String signature) {
        if (signature == null || Instant.now().getEpochSecond() > expiresEpochSecond) {
            return false;
        }
        return MessageDigest.isEqual(sign(videoId, expiresEpochSecond).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
