package com.adrifit.backend.notification.service;

import com.adrifit.backend.common.settings.AppSetting;
import com.adrifit.backend.common.settings.AppSettingRepository;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.time.Instant;
import java.util.Base64;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VAPID key pair used to sign Web Push requests. Taken from configuration (VAPID_PUBLIC_KEY /
 * VAPID_PRIVATE_KEY) or generated once and stored in {@code app_settings}: it must never change,
 * otherwise every existing browser subscription stops working.
 */
@Service
public class VapidKeyService {

    private static final Logger log = LoggerFactory.getLogger(VapidKeyService.class);
    static final String PUBLIC_KEY = "push.vapid.public-key";
    static final String PRIVATE_KEY = "push.vapid.private-key";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final AppSettingRepository settings;
    private final String configuredPublic;
    private final String configuredPrivate;
    private volatile String[] cached;

    public VapidKeyService(AppSettingRepository settings,
                           @Value("${adrifit.push.vapid-public-key:}") String configuredPublic,
                           @Value("${adrifit.push.vapid-private-key:}") String configuredPrivate) {
        this.settings = settings;
        this.configuredPublic = configuredPublic;
        this.configuredPrivate = configuredPrivate;
    }

    /** Public key (base64url, uncompressed point) the browser needs to subscribe. */
    public String publicKey() {
        return keys()[0];
    }

    public String privateKey() {
        return keys()[1];
    }

    @Transactional
    public synchronized String[] keys() {
        if (cached != null) {
            return cached;
        }
        if (!configuredPublic.isBlank() && !configuredPrivate.isBlank()) {
            cached = new String[]{configuredPublic.trim(), configuredPrivate.trim()};
            return cached;
        }
        String pub = settings.findById(PUBLIC_KEY).map(AppSetting::getValue).orElse(null);
        String priv = settings.findById(PRIVATE_KEY).map(AppSetting::getValue).orElse(null);
        if (pub == null || priv == null) {
            String[] generated = generate();
            pub = generated[0];
            priv = generated[1];
            Instant now = Instant.now();
            settings.save(new AppSetting(PUBLIC_KEY, pub, now));
            settings.save(new AppSetting(PRIVATE_KEY, priv, now));
            log.info("Generated a new VAPID key pair for Web Push (stored in app_settings)");
        }
        cached = new String[]{pub, priv};
        return cached;
    }

    static String[] generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));
            KeyPair pair = generator.generateKeyPair();
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return new String[]{
                    encoder.encodeToString(Utils.encode((ECPublicKey) pair.getPublic())),
                    encoder.encodeToString(Utils.encode((ECPrivateKey) pair.getPrivate()))
            };
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate VAPID keys", e);
        }
    }
}
