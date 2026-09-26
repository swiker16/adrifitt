package com.adrifit.backend.common.event;

/**
 * Published (synchronously, inside the delete transaction) right before a client is removed,
 * so every module can delete the rows that reference it.
 */
public record ClientDeletedEvent(Long clientId, Long userId) {
}
