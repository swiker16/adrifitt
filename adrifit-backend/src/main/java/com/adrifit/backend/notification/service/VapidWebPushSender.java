package com.adrifit.backend.notification.service;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Real Web Push delivery: VAPID-signed, aes128gcm-encrypted (RFC 8291/8292). */
@Component
public class VapidWebPushSender implements WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(VapidWebPushSender.class);

    private final VapidKeyService keys;
    private final String subject;
    private volatile PushService pushService;

    public VapidWebPushSender(VapidKeyService keys,
                              @Value("${adrifit.push.subject:mailto:hola@adrifit.app}") String subject) {
        this.keys = keys;
        this.subject = subject;
    }

    @Override
    public int send(String endpoint, String p256dh, String auth, String payloadJson) throws Exception {
        Notification notification = new Notification(endpoint, p256dh, auth, payloadJson);
        HttpResponse response = service().send(notification, Encoding.AES128GCM);
        int status = response.getStatusLine().getStatusCode();
        if (response.getEntity() != null) {
            if (status >= 300) {
                String body = EntityUtils.toString(response.getEntity());
                log.warn("Push service {} answered HTTP {}: {}", host(endpoint), status,
                        body.length() > 300 ? body.substring(0, 300) : body);
            } else {
                EntityUtils.consumeQuietly(response.getEntity());
            }
        }
        return status;
    }

    private static String host(String endpoint) {
        try {
            return java.net.URI.create(endpoint).getHost();
        } catch (IllegalArgumentException e) {
            return "?";
        }
    }

    private PushService service() throws Exception {
        if (pushService == null) {
            synchronized (this) {
                if (pushService == null) {
                    pushService = new PushService(keys.publicKey(), keys.privateKey(), subject);
                }
            }
        }
        return pushService;
    }
}
