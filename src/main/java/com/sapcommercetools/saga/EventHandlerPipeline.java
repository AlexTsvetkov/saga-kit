package com.sapcommercetools.saga;

/**
 * Wraps an event handler with idempotency, the 200/400/409 retry-semantics mapping, tracing and DLQ handling so handlers only declare intent.
 *
 * <p>This is the core abstraction of <b>saga-kit</b>. The starter implementation
 * below is intentionally minimal — a foundation that documents the intended
 * contract and gives tests something real to exercise.
 */
public final class EventHandlerPipeline {

    /**
     * Returns a human-readable description of what this component does.
     * Replace with the real behaviour as the project grows.
     */
    public String describe() {
        return "saga-kit: Eventing-correctness library for SAP BTP/Kyma commerce meshes — idempotency, ordering, transactional outbox and DLQ as declarative middleware.";
    }

    /**
     * Placeholder for the primary operation. Kept trivial and total so the
     * scaffold builds and tests pass on a clean checkout.
     *
     * @param input a caller-supplied token
     * @return {@code true} when the input is non-blank
     */
    public boolean accepts(String input) {
        return input != null && !input.isBlank();
    }
}
