package com.sapcommercetools.saga.examples;

import com.sapcommercetools.saga.Event;
import com.sapcommercetools.saga.EventHandlerPipeline;
import com.sapcommercetools.saga.HandleResult;
import com.sapcommercetools.saga.TransientException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable, self-contained tutorial for <b>saga-kit</b>.
 *
 * <p>It demonstrates the 200/400/409 acknowledgement contract that
 * {@link EventHandlerPipeline} enforces around a plain {@link com.sapcommercetools.saga.EventHandler}:
 *
 * <ul>
 *   <li><b>Fresh event</b> → handler runs, {@code 200} acknowledged.</li>
 *   <li><b>Duplicate id</b> → {@code 409} acknowledged, handler is NOT re-run
 *       (proven with an invocation counter).</li>
 *   <li><b>Transient failure</b> → {@code 400} (not acknowledged, please retry)
 *       until the attempt budget is exhausted, then dead-lettered as
 *       {@code 200} with {@code sentToDlq=true}.</li>
 *   <li><b>Non-transient failure</b> → acknowledge-and-discard: {@code 200},
 *       no retry, no DLQ.</li>
 * </ul>
 *
 * <p>Pure JDK, no broker required.
 */
public final class Example {

    private Example() {
        // tutorial entry-point only
    }

    public static void main(String[] args) {
        System.out.println("=== saga-kit: 200/400/409 pipeline tutorial ===\n");

        scenarioFreshAndDuplicate();
        scenarioTransientRetriesThenDlq();
        scenarioNonTransientDiscard();

        System.out.println("=== end of tutorial ===");
    }

    /**
     * Scenario A: a fresh event succeeds (200), and re-delivering the SAME id is
     * a duplicate (409) that does NOT re-run the handler. We prove the handler
     * is skipped with an invocation counter.
     */
    private static void scenarioFreshAndDuplicate() {
        System.out.println("-- Scenario A: fresh event (200) then duplicate (409, handler skipped) --");

        // Counts how many times the business handler actually executes.
        AtomicInteger handlerRuns = new AtomicInteger();

        // A DLQ sink; here it should never be used.
        List<Event> dlq = new ArrayList<>();

        EventHandlerPipeline pipeline = new EventHandlerPipeline(
                event -> {
                    handlerRuns.incrementAndGet();       // side effect we want to run at most once
                    System.out.println("      [handler] processing " + event.id());
                },
                dlq::add);

        Event e = Event.of("evt-A", "OrderPlaced");

        HandleResult first = pipeline.dispatch(e);
        System.out.println("  1st delivery : " + first);
        System.out.println("               isOk=" + first.isOk() + " attempts(evt-A)=" + pipeline.attempts("evt-A")
                + " handlerRuns=" + handlerRuns.get());

        HandleResult second = pipeline.dispatch(e);   // same id -> duplicate
        System.out.println("  2nd delivery : " + second);
        System.out.println("               isDuplicate=" + second.isDuplicate()
                + " handlerRuns=" + handlerRuns.get() + " (unchanged -> handler was skipped)");
        System.out.println("  DLQ size     : " + dlq.size());
        System.out.println();
    }

    /**
     * Scenario B: a handler that always fails transiently. With maxAttempts=3 the
     * first two deliveries return 400 (retry, not acknowledged) and the third
     * exhausts the budget and dead-letters (200, sentToDlq=true).
     */
    private static void scenarioTransientRetriesThenDlq() {
        System.out.println("-- Scenario B: transient failure retries (400) then DLQs at max attempts (200) --");

        AtomicInteger handlerRuns = new AtomicInteger();
        List<Event> dlq = new ArrayList<>();

        int maxAttempts = 3;
        EventHandlerPipeline pipeline = new EventHandlerPipeline(
                event -> {
                    handlerRuns.incrementAndGet();
                    // Always transient -> the pipeline should retry until exhausted.
                    throw new TransientException("downstream 503 (attempt observed by handler)");
                },
                maxAttempts,
                dlq::add);

        Event e = Event.of("evt-B", "InventorySync");

        // Simulate the broker redelivering the same event until it is acknowledged.
        for (int delivery = 1; delivery <= maxAttempts; delivery++) {
            HandleResult r = pipeline.dispatch(e);
            System.out.println("  delivery " + delivery + " : " + r);
            System.out.println("             isRetry=" + r.isRetry() + " acknowledged=" + r.acknowledged()
                    + " sentToDlq=" + r.sentToDlq() + " attempts(evt-B)=" + pipeline.attempts("evt-B"));
        }
        System.out.println("  handlerRuns : " + handlerRuns.get() + " (ran once per delivery)");
        System.out.println("  DLQ size    : " + dlq.size() + " -> " + dlq);
        System.out.println();
    }

    /**
     * Scenario C: a handler that throws a NON-transient (permanent) exception.
     * The pipeline acknowledges-and-discards: status 200, no retry, no DLQ.
     */
    private static void scenarioNonTransientDiscard() {
        System.out.println("-- Scenario C: non-transient failure discarded (200, no DLQ) --");

        AtomicInteger handlerRuns = new AtomicInteger();
        List<Event> dlq = new ArrayList<>();

        EventHandlerPipeline pipeline = new EventHandlerPipeline(
                event -> {
                    handlerRuns.incrementAndGet();
                    // A plain RuntimeException is NOT a TransientException -> permanent.
                    throw new IllegalStateException("malformed payload, cannot ever succeed");
                },
                dlq::add);

        Event e = Event.of("evt-C", "BadMessage");

        HandleResult r = pipeline.dispatch(e);
        System.out.println("  delivery    : " + r);
        System.out.println("              isOk=" + r.isOk() + " acknowledged=" + r.acknowledged()
                + " sentToDlq=" + r.sentToDlq() + " attempts(evt-C)=" + pipeline.attempts("evt-C"));
        System.out.println("  handlerRuns : " + handlerRuns.get());
        System.out.println("  DLQ size    : " + dlq.size() + " (never dead-lettered)");
        System.out.println();
    }
}
