package com.adrifit.backend.common.security;

import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adrifit.security.jwt")
public class JwtProperties {

    private static final Logger log = LoggerFactory.getLogger(JwtProperties.class);

    /** Base64 HMAC key from JWT_SECRET. Never hardcoded: without it a random key is generated. */
    private String secret;
    private long expirationMs;
    private volatile String generated;

    /**
     * The signing secret. When JWT_SECRET is not configured a random 512-bit key is created for
     * this run (secure, but sessions end when the server restarts): set JWT_SECRET in production.
     */
    public String getSecret() {
        if (secret != null && !secret.isBlank()) {
            return secret;
        }
        if (generated == null) {
            synchronized (this) {
                if (generated == null) {
                    byte[] key = new byte[64];
                    new SecureRandom().nextBytes(key);
                    generated = Base64.getEncoder().encodeToString(key);
                    log.warn("JWT_SECRET is not set: using a random key for this run (sessions end on restart). "
                            + "Set JWT_SECRET (Base64, 256+ bits) in production.");
                }
            }
        }
        return generated;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
