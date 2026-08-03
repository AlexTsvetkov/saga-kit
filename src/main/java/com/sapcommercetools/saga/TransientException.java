package com.sapcommercetools.saga;

/**
 * Marks a failure as <b>transient</b> — i.e. worth retrying (network blip,
 * lock contention, downstream 503, optimistic-lock clash, …).
 *
 * <p>When an {@link EventHandler} throws this, the {@link EventHandlerPipeline}
 * returns status {@code 400} to request redelivery, up to the configured
 * maximum number of delivery attempts. Any other exception type is treated as
 * permanent and is acknowledged-and-discarded (never retried, never DLQ'd).
 */
public class TransientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message a description of the transient fault
     */
    public TransientException(String message) {
        super(message);
    }

    /**
     * @param message a description of the transient fault
     * @param cause   the underlying cause
     */
    public TransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
