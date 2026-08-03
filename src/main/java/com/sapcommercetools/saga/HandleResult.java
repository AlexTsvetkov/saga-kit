package com.sapcommercetools.saga;

/**
 * The outcome of dispatching a single {@link Event} through the
 * {@link EventHandlerPipeline}.
 *
 * <p>The {@code status} field mirrors the HTTP-style acknowledgement contract a
 * broker (or webhook endpoint) uses to decide whether to redeliver. See
 * {@link EventHandlerPipeline} for the full mapping:
 *
 * <ul>
 *   <li><b>200</b> — stop redelivery (acknowledged: success, discarded, or DLQ'd).</li>
 *   <li><b>400</b> — trigger a retry (transient failure, not yet exhausted).</li>
 *   <li><b>409</b> — duplicate; treated as an idempotent success (acknowledged).</li>
 * </ul>
 *
 * @param status       the HTTP-style status code (200 / 400 / 409)
 * @param acknowledged whether the broker should stop redelivering this event
 * @param sentToDlq    whether the event was routed to the dead-letter queue
 * @param note         a short human-readable explanation of the outcome
 */
public record HandleResult(int status, boolean acknowledged, boolean sentToDlq, String note) {

    /** @return {@code true} when {@code status == 200}. */
    public boolean isOk() {
        return status == 200;
    }

    /** @return {@code true} when {@code status == 400} (retry requested). */
    public boolean isRetry() {
        return status == 400;
    }

    /** @return {@code true} when {@code status == 409} (duplicate). */
    public boolean isDuplicate() {
        return status == 409;
    }
}
