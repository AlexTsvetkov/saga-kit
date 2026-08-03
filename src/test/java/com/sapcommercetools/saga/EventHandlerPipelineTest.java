package com.sapcommercetools.saga;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EventHandlerPipelineTest {

    private final List<Event> dlq = new ArrayList<>();

    private EventHandlerPipeline pipeline(EventHandler handler, int maxAttempts) {
        return new EventHandlerPipeline(handler, maxAttempts, dlq::add);
    }

    @Test
    void happy_path_returns_200_acknowledged() {
        AtomicInteger calls = new AtomicInteger();
        EventHandlerPipeline p = pipeline(e -> calls.incrementAndGet(), 3);

        HandleResult r = p.dispatch(Event.of("evt-1", "payload"));

        assertEquals(200, r.status());
        assertTrue(r.acknowledged());
        assertFalse(r.sentToDlq());
        assertTrue(r.isOk());
        assertEquals(1, calls.get());
        assertTrue(p.hasSeen("evt-1"));
        assertEquals(1, p.attempts("evt-1"));
        assertTrue(dlq.isEmpty());
    }

    @Test
    void duplicate_returns_409_without_rerunning_handler() {
        AtomicInteger calls = new AtomicInteger();
        EventHandlerPipeline p = pipeline(e -> calls.incrementAndGet(), 3);

        HandleResult first = p.dispatch(Event.of("dup-1", "x"));
        HandleResult second = p.dispatch(Event.of("dup-1", "x"));

        assertEquals(200, first.status());
        assertEquals(409, second.status());
        assertTrue(second.isDuplicate());
        assertTrue(second.acknowledged());
        assertEquals("duplicate", second.note());
        // Handler ran exactly once despite two deliveries.
        assertEquals(1, calls.get());
        // Duplicate short-circuit does not increment the attempt counter.
        assertEquals(1, p.attempts("dup-1"));
    }

    @Test
    void transient_failure_returns_400_then_dlqs_at_max_attempts() {
        // Always transiently fails.
        EventHandlerPipeline p = pipeline(e -> { throw new TransientException("db locked"); }, 3);
        Event e = Event.of("flaky-1", "x");

        HandleResult a1 = p.dispatch(e);
        assertEquals(400, a1.status());
        assertFalse(a1.acknowledged());
        assertFalse(a1.sentToDlq());
        assertFalse(p.hasSeen("flaky-1"), "id must stay unseen so a retry can re-run it");
        assertEquals(1, p.attempts("flaky-1"));

        HandleResult a2 = p.dispatch(e);
        assertEquals(400, a2.status());
        assertFalse(a2.acknowledged());
        assertEquals(2, p.attempts("flaky-1"));

        // Third (final) attempt exhausts the budget → DLQ.
        HandleResult a3 = p.dispatch(e);
        assertEquals(200, a3.status());
        assertTrue(a3.acknowledged());
        assertTrue(a3.sentToDlq());
        assertEquals("dlq: max attempts", a3.note());
        assertEquals(3, p.attempts("flaky-1"));
        assertTrue(p.hasSeen("flaky-1"));
        assertEquals(1, dlq.size());
        assertEquals("flaky-1", dlq.get(0).id());
    }

    @Test
    void transient_then_success_before_exhaustion_acknowledges() {
        AtomicInteger calls = new AtomicInteger();
        EventHandler flakyThenOk = e -> {
            if (calls.incrementAndGet() < 2) {
                throw new TransientException("temporary");
            }
        };
        EventHandlerPipeline p = pipeline(flakyThenOk, 3);
        Event e = Event.of("recover-1", "x");

        HandleResult first = p.dispatch(e);
        assertEquals(400, first.status());
        assertFalse(first.acknowledged());

        HandleResult second = p.dispatch(e);
        assertEquals(200, second.status());
        assertTrue(second.acknowledged());
        assertFalse(second.sentToDlq());
        assertTrue(dlq.isEmpty());
        assertEquals(2, p.attempts("recover-1"));
    }

    @Test
    void non_transient_is_discarded_200_not_dlq() {
        EventHandlerPipeline p = pipeline(e -> { throw new IllegalStateException("bad payload"); }, 3);

        HandleResult r = p.dispatch(Event.of("poison-1", "x"));

        assertEquals(200, r.status());
        assertTrue(r.acknowledged());
        assertFalse(r.sentToDlq());
        assertEquals("discarded: bad payload", r.note());
        // Marked seen so a redelivery is treated as a duplicate, not re-run.
        assertTrue(p.hasSeen("poison-1"));
        assertTrue(dlq.isEmpty());

        HandleResult redelivery = p.dispatch(Event.of("poison-1", "x"));
        assertEquals(409, redelivery.status());
    }

    @Test
    void attempts_tracking_starts_at_zero_and_increments_per_dispatch() {
        EventHandlerPipeline p = pipeline(e -> { throw new TransientException("x"); }, 5);
        assertEquals(0, p.attempts("unknown"));

        p.dispatch(Event.of("count-1", "a"));
        assertEquals(1, p.attempts("count-1"));
        p.dispatch(Event.of("count-1", "a"));
        assertEquals(2, p.attempts("count-1"));
    }

    @Test
    void max_attempts_one_dlqs_immediately() {
        EventHandlerPipeline p = pipeline(e -> { throw new TransientException("x"); }, 1);

        HandleResult r = p.dispatch(Event.of("single-1", "x"));

        assertEquals(200, r.status());
        assertTrue(r.sentToDlq());
        assertEquals(1, dlq.size());
    }

    @Test
    void constructor_rejects_invalid_arguments() {
        assertThrows(NullPointerException.class, () -> new EventHandlerPipeline(null, dlq::add));
        assertThrows(NullPointerException.class,
                () -> new EventHandlerPipeline(e -> {}, 3, null));
        assertThrows(IllegalArgumentException.class,
                () -> new EventHandlerPipeline(e -> {}, 0, dlq::add));
    }

    @Test
    void describes_itself() {
        EventHandlerPipeline p = pipeline(e -> {}, 3);
        assertTrue(p.describe().startsWith("saga-kit"));
        assertTrue(p.accepts("cart-123"));
        assertFalse(p.accepts(" "));
    }
}
