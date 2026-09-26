package com.adrifit.backend.notification.service;

/**
 * Delivers one encrypted push message to a browser push service (FCM, Mozilla, Apple...).
 * Returns the HTTP status answered by the push service.
 */
public interface WebPushSender {

    int send(String endpoint, String p256dh, String auth, String payloadJson) throws Exception;
}
