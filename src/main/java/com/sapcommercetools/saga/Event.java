package com.sapcommercetools.saga;

import java.util.Objects;

/**
 * An immutable inbound event to be processed by an {@link EventHandlerPipeline}.
 *
 * <p>The {@code id} is the idempotency key: two deliveries carrying the same
 * {@code id} are treated as the same logical event, so the handler side effect
 * runs at most once.
 *
 * @param id      the idempotency key (must not be {@code null})
 * @param type    a logical type/topic name (may be {@code null})
 * @param payload the opaque business payload (may be {@code null})
 */
public record Event(String id, String type, Object payload) {

    /**
     * @throws NullPointerException if {@code id} is {@code null}
     */
    public Event {
        Objects.requireNonNull(id, "event id must not be null");
    }

    /**
     * Convenience factory for an event with only an id and payload.
     *
     * @param id      the idempotency key
     * @param payload the business payload
     * @return a new event with a {@code null} type
     */
    public static Event of(String id, Object payload) {
        return new Event(id, null, payload);
    }
}
