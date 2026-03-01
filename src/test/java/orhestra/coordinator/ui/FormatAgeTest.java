package orhestra.coordinator.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormatAgeTest {

    @Test
    void shortDurations() {
        assertEquals("0s ago", ExecutionController.formatAge(0));
        assertEquals("1s ago", ExecutionController.formatAge(1));
        assertEquals("5s ago", ExecutionController.formatAge(5));
        assertEquals("59s ago", ExecutionController.formatAge(59));
    }

    @Test
    void minuteRanges() {
        assertEquals("1m0s ago", ExecutionController.formatAge(60));
        assertEquals("1m5s ago", ExecutionController.formatAge(65));
        assertEquals("2m30s ago", ExecutionController.formatAge(150));
        assertEquals("59m59s ago", ExecutionController.formatAge(3599));
    }

    @Test
    void hourRanges() {
        assertEquals("1h0m ago", ExecutionController.formatAge(3600));
        assertEquals("1h30m ago", ExecutionController.formatAge(5400));
        assertEquals("2h0m ago", ExecutionController.formatAge(7200));
    }
}
