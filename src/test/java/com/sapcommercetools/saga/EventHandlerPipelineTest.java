package com.sapcommercetools.saga;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EventHandlerPipelineTest {

    private final EventHandlerPipeline subject = new EventHandlerPipeline();

    @Test
    void describes_itself() {
        assertTrue(subject.describe().startsWith("saga-kit"));
    }

    @Test
    void accepts_non_blank_input() {
        assertTrue(subject.accepts("cart-123"));
        assertFalse(subject.accepts(" "));
        assertFalse(subject.accepts(null));
    }
}
