package com.sapcommercetools.saga;

/**
 * A side-effecting event handler. Implementations declare only business intent;
 * idempotency, retry semantics and dead-lettering are supplied by the
 * {@link EventHandlerPipeline} that wraps the handler.
 *
 * <p>Signal retry intent by throwing {@link TransientException}; any other
 * thrown exception is treated as a permanent (non-retryable) failure.
 */
@FunctionalInterface
public interface EventHandler {

    /**
     * Process the event, performing its business side effect.
     *
     * @param e the event to handle
     * @throws TransientException to request a retry (transient/recoverable fault)
     * @throws Exception          any other exception marks a permanent failure
     */
    void handle(Event e) throws Exception;
}
