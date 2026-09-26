package com.adrifit.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.common.settings.AppSettingRepository;
import com.adrifit.backend.notification.service.VapidKeyService;
import com.adrifit.backend.notification.service.VapidWebPushSender;
import com.sun.net.httpserver.HttpServer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Sends a real Web Push request to a local HTTP "push service" and decrypts it as a browser
 * would (RFC 8291 aes128gcm), checking the VAPID authorization header (RFC 8292) as well.
 */
class WebPushEncryptionTest {

    @Test
    void messageIsVapidSignedAndDecryptableByTheBrowserKeys() throws Exception {
        // Browser side: subscription keys.
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair browser = gen.generateKeyPair();
        byte[] browserPublic = uncompressed((ECPublicKey) browser.getPublic());
        byte[] authSecret = new byte[16];
        new SecureRandom().nextBytes(authSecret);

        // Local push service capturing the request.
        AtomicReference<byte[]> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> encoding = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push/abc", exchange -> {
            body.set(exchange.getRequestBody().readAllBytes());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            encoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
        try {
            AppSettingRepository settings = Mockito.mock(AppSettingRepository.class);
            Mockito.when(settings.findById(Mockito.anyString())).thenReturn(Optional.empty());
            VapidKeyService vapid = new VapidKeyService(settings, "", "");
            VapidWebPushSender sender = new VapidWebPushSender(vapid, "mailto:test@adrifitt.app");

            String payload = "{\"notification\":{\"title\":\"Hola\",\"body\":\"Tienes un mensaje\"}}";
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/push/abc";
            Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
            int status = sender.send(endpoint, b64.encodeToString(browserPublic), b64.encodeToString(authSecret), payload);

            assertThat(status).isEqualTo(201);
            assertThat(encoding.get()).isEqualTo("aes128gcm");
            assertThat(authorization.get()).startsWith("vapid t=").contains("k=" + vapid.publicKey());
            assertThat(decrypt(body.get(), browser, browserPublic, authSecret)).isEqualTo(payload);
        } finally {
            server.stop(0);
        }
    }

    /** RFC 8291 §3 decryption, as done by the browser. */
    private static String decrypt(byte[] message, KeyPair browser, byte[] browserPublic, byte[] authSecret) throws Exception {
        ByteBuffer buf = ByteBuffer.wrap(message);
        byte[] salt = new byte[16];
        buf.get(salt);
        buf.getInt(); // record size
        byte[] serverPublic = new byte[buf.get() & 0xFF];
        buf.get(serverPublic);
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(publicKey(serverPublic, (ECPublicKey) browser.getPublic()), true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), browserPublic, serverPublic, new byte[]{1});
        byte[] ikm = hmac(prkKey, keyInfo);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = cipher.doFinal(ciphertext);
        int end = plain.length - 1;
        while (end >= 0 && plain[end] == 0) end--; // padding
        assertThat(plain[end]).isEqualTo((byte) 2); // last-record delimiter
        return new String(plain, 0, end, StandardCharsets.UTF_8);
    }

    private static java.security.PublicKey publicKey(byte[] uncompressed, ECPublicKey sameCurve) throws Exception {
        byte[] x = Arrays.copyOfRange(uncompressed, 1, 33);
        byte[] y = Arrays.copyOfRange(uncompressed, 33, 65);
        ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, sameCurve.getParams()));
    }

    private static byte[] uncompressed(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        copy32(key.getW().getAffineX(), out, 1);
        copy32(key.getW().getAffineY(), out, 33);
        return out;
    }

    private static void copy32(BigInteger value, byte[] out, int offset) {
        byte[] raw = value.toByteArray();
        int len = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - len, out, offset + 32 - len, len);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
