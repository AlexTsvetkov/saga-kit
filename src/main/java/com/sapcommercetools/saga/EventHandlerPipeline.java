package com.sapcommercetools.saga;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Wraps an {@link EventHandler} with idempotency, an HTTP-style retry-status
 * mapping and a dead-letter queue, so handlers only declare business intent.
 *
 * <h2>The 200 / 400 / 409 acknowledgement contract</h2>
 *
 * A message broker (or webhook endpoint) decides whether to redeliver an event
 * based on the acknowledgement it receives. This pipeline encodes that decision
 * as a status code, which is the core domain invariant of saga-kit:
 *
 * <ul>
 *   <li><b>200 — acknowledge, stop redelivery.</b> Returned on handler success,
 *       on permanent (non-transient) failure that is discarded, and when an
 *       event is routed to the DLQ after exhausting its retries. In every 200
 *       case the broker must consider the event done.</li>
 *   <li><b>400 — negative-acknowledge, please retry.</b> Returned when the
 *       handler throws a {@link TransientException} and delivery attempts are
 *       not yet exhausted. The event is <em>not</em> acknowledged, so the broker
 *       redelivers it.</li>
 *   <li><b>409 — duplicate, treated as success.</b> Returned when the event id
 *       has already been processed. The handler is <em>not</em> re-run; the
 *       event is acknowledged. This is the idempotency guarantee: at-least-once
 *       delivery is made effectively exactly-once.</li>
 * </ul>
 *
 * <h2>Per-outcome semantics of {@link #dispatch(Event)}</h2>
 *
 * <ol>
 *   <li><b>Already seen id</b> → {@code 409}, acknowledged, note {@code "duplicate"};
 *       handler is skipped.</li>
 *   <li><b>Handler succeeds</b> → id marked seen, {@code 200}, acknowledged.</li>
 *   <li><b>Handler throws {@link TransientException}</b> and this was not the
 *       final attempt → {@code 400}, <em>not</em> acknowledged; the id remains
 *       unseen so a retry can re-run it. On the final attempt the retry budget
 *       is exhausted, so the event is sent to the DLQ, the id is marked seen and
 *       the result is {@code 200}, acknowledged, {@code sentToDlq=true}, note
 *       {@code "dlq: max attempts"}.</li>
 *   <li><b>Handler throws any other exception</b> → acknowledge-and-discard: id
 *       marked seen, {@code 200}, acknowledged, note {@code "discarded: <msg>"}.
 *       Never retried, never DLQ'd.</li>
 * </ol>
 *
 * <p>This class is thread-safe for concurrent {@link #dispatch(Event)} calls via
 * intrinsic locking; a single logical event is processed under the monitor.
 */
public final class EventHandlerPipeline {

    /** Default maximum number of delivery attempts before dead-lettering. */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final EventHandler handler;
    private final int maxAttempts;
    private final Consumer<Event> dlq;
    private final int maxSeen;

    /** Idempotency store: ids of events that have reached a terminal state. */
    private final Set<String> seen = new HashSet<>();
    /** Insertion-ordered view of {@link #seen} used to bound its size (FIFO eviction). */
    private final Deque<String> seenOrder = new ArrayDeque<>();
    /** Per-id delivery attempt counter (transient retries). */
    private final Map<String, Integer> attempts = new HashMap<>();

    /**
     * Creates a pipeline with the {@linkplain #DEFAULT_MAX_ATTEMPTS default}
     * attempt budget and an unbounded idempotency store.
     *
     * @param handler the business handler to wrap
     * @param dlq     consumer invoked once when an event is dead-lettered
     */
    public EventHandlerPipeline(EventHandler handler, Consumer<Event> dlq) {
        this(handler, DEFAULT_MAX_ATTEMPTS, dlq, Integer.MAX_VALUE);
    }

    /**
     * Creates a pipeline with an explicit attempt budget.
     *
     * @param handler     the business handler to wrap
     * @param maxAttempts maximum delivery attempts before dead-lettering (&ge; 1)
     * @param dlq         consumer invoked once when an event is dead-lettered
     */
    public EventHandlerPipeline(EventHandler handler, int maxAttempts, Consumer<Event> dlq) {
        this(handler, maxAttempts, dlq, Integer.MAX_VALUE);
    }

    /**
     * Full constructor.
     *
     * @param handler     the business handler to wrap (must not be {@code null})
     * @param maxAttempts maximum delivery attempts before dead-lettering (&ge; 1)
     * @param dlq         consumer invoked once when an event is dead-lettered
     *                    (must not be {@code null})
     * @param maxSeen     maximum size of the idempotency store; oldest ids are
     *                    evicted FIFO beyond this bound (&ge; 1)
     * @throws NullPointerException     if {@code handler} or {@code dlq} is null
     * @throws IllegalArgumentException if {@code maxAttempts} or {@code maxSeen} &lt; 1
     */
    public EventHandlerPipeline(EventHandler handler, int maxAttempts, Consumer<Event> dlq, int maxSeen) {
        this.handler = Objects.requireNonNull(handler, "handler must not be null");
        this.dlq = Objects.requireNonNull(dlq, "dlq must not be null");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, was " + maxAttempts);
        }
        if (maxSeen < 1) {
            throw new IllegalArgumentException("maxSeen must be >= 1, was " + maxSeen);
        }
        this.maxAttempts = maxAttempts;
        this.maxSeen = maxSeen;
    }

    /**
     * Dispatches one delivery of an event through the pipeline, applying the
     * idempotency check and the 200/400/409 mapping described in the class
     * Javadoc.
     *
     * @param event the event delivery to process (must not be {@code null})
     * @return the {@link HandleResult} describing the outcome
     * @throws NullPointerException if {@code event} is null
     */
    public synchronized HandleResult dispatch(Event event) {
        Objects.requireNonNull(event, "event must not be null");
        final String id = event.id();

        // 1. Idempotency: an already-terminal id is a duplicate — success, no re-run.
        if (seen.contains(id)) {
            return new HandleResult(409, true, false, "duplicate");
        }

        // This delivery consumes one attempt.
        int attempt = attempts.merge(id, 1, Integer::sum);

        try {
            handler.handle(event);
            // 2. Success — mark terminal, acknowledge.
            markSeen(id);
            return new HandleResult(200, true, false, "ok");
        } catch (TransientException te) {
            // 3. Transient failure.
            if (attempt >= maxAttempts) {
                // Retry budget exhausted → dead-letter, terminal, acknowledge.
                dlq.accept(event);
                markSeen(id);
                return new HandleResult(200, true, true, "dlq: max attempts");
            }
            // Not yet exhausted → request redelivery; id stays unseen for the retry.
            return new HandleResult(400, false, false,
                    "retry " + attempt + "/" + maxAttempts + ": " + te.getMessage());
        } catch (Exception permanent) {
            // 4. Non-transient failure → acknowledge-and-discard (no retry, no DLQ).
            markSeen(id);
            return new HandleResult(200, true, false, "discarded: " + permanent.getMessage());
        }
    }

    /**
     * Returns how many times {@link #dispatch(Event)} has attempted to run the
     * handler for the given id (duplicate short-circuits do not count, since the
     * handler is not invoked for them).
     *
     * @param id an event id
     * @return the attempt count, or {@code 0} if never dispatched
     */
    public synchronized int attempts(String id) {
        return attempts.getOrDefault(id, 0);
    }

    /**
     * @param id an event id
     * @return {@code true} if the id has reached a terminal state and is stored
     *         in the idempotency set
     */
    public synchronized boolean hasSeen(String id) {
        return seen.contains(id);
    }

    private void markSeen(String id) {
        if (seen.add(id)) {
            seenOrder.addLast(id);
            // Bound the idempotency store: evict the oldest terminal ids (FIFO).
            while (seenOrder.size() > maxSeen) {
                String evicted = seenOrder.pollFirst();
                seen.remove(evicted);
            }
        }
    }

    /**
     * Returns a human-readable description of what this component does.
     *
     * @return a one-line summary
     */
    public String describe() {
        return "saga-kit: Eventing-correctness library for SAP BTP/Kyma commerce meshes — idempotency, "
                + "the 200/400/409 retry-status mapping, transactional outbox and DLQ as declarative middleware.";
    }

    /**
     * Reports whether a caller-supplied token is usable as an event id.
     *
     * @param input a caller-supplied token
     * @return {@code true} when the input is non-blank
     */
    public boolean accepts(String input) {
        return input != null && !input.isBlank();
    }
}
