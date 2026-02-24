package orhestra.coordinator.api.v1.dto;

import orhestra.coordinator.model.Task;
import orhestra.coordinator.model.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaskResultResponse DTO mapping.
 * Verifies that payload JSON is correctly parsed into
 * algorithm/iterations/agents/dimension fields.
 */
class TaskResultResponseTest {

    /** Helper: build a Task with the given payload and optional result fields. */
    private static Task taskWithPayload(String payload) {
        return Task.builder()
                .id("task-001")
                .jobId("job-001")
                .payload(payload)
                .status(TaskStatus.DONE)
                .assignedTo("spot-abc")
                .runtimeMs(1500L)
                .iter(100)
                .fopt(0.001)
                .createdAt(Instant.parse("2026-01-28T16:00:00Z"))
                .startedAt(Instant.parse("2026-01-28T16:01:00Z"))
                .finishedAt(Instant.parse("2026-01-28T16:02:00Z"))
                .build();
    }

    @Test
    @DisplayName("Happy path: full payload populates all fields")
    void fromTask_fullPayload() {
        String payload = """
                {"alg":"PSO","iterations":{"max":100},"agents":10,"dimension":2}
                """;

        TaskResultResponse dto = TaskResultResponse.from(taskWithPayload(payload));

        assertEquals("task-001", dto.taskId());
        assertEquals("DONE", dto.status());
        assertEquals("PSO", dto.algorithm());
        assertEquals(100, dto.iterations());
        assertEquals(10, dto.agents());
        assertEquals(2, dto.dimension());
        assertEquals(1500L, dto.runtimeMs());
        assertEquals(100, dto.iter());
        assertEquals(0.001, dto.fopt());
        assertEquals("spot-abc", dto.assignedTo());
        assertNotNull(dto.startedAt());
        assertNotNull(dto.finishedAt());
    }

    @Test
    @DisplayName("Iterations as plain number (backward compat)")
    void fromTask_iterationsAsNumber() {
        String payload = """
                {"alg":"GA","iterations":200,"agents":50,"dimension":5}
                """;

        TaskResultResponse dto = TaskResultResponse.from(taskWithPayload(payload));

        assertEquals("GA", dto.algorithm());
        assertEquals(200, dto.iterations());
        assertEquals(50, dto.agents());
        assertEquals(5, dto.dimension());
    }

    @Test
    @DisplayName("Partial payload: missing fields are null")
    void fromTask_partialPayload() {
        String payload = """
                {"alg":"DE"}
                """;

        TaskResultResponse dto = TaskResultResponse.from(taskWithPayload(payload));

        assertEquals("DE", dto.algorithm());
        assertNull(dto.iterations());
        assertNull(dto.agents());
        assertNull(dto.dimension());
    }

    @Test
    @DisplayName("Empty JSON object: all parsed fields null")
    void fromTask_emptyPayload() {
        TaskResultResponse dto = TaskResultResponse.from(taskWithPayload("{}"));

        assertNull(dto.algorithm());
        assertNull(dto.iterations());
        assertNull(dto.agents());
        assertNull(dto.dimension());
        // Non-payload fields still come from Task
        assertEquals("task-001", dto.taskId());
        assertEquals(1500L, dto.runtimeMs());
    }

    @Test
    @DisplayName("Malformed JSON: no crash, all parsed fields null")
    void fromTask_malformedJson() {
        TaskResultResponse dto = TaskResultResponse.from(taskWithPayload("{bad json!!!"));

        assertNull(dto.algorithm());
        assertNull(dto.iterations());
        assertNull(dto.agents());
        assertNull(dto.dimension());
        // Non-payload fields still populated
        assertEquals("task-001", dto.taskId());
        assertEquals("DONE", dto.status());
    }

    @Test
    @DisplayName("Null payload: no crash, all parsed fields null")
    void fromTask_nullPayload() {
        // payload is required by builder, use empty JSON as closest equivalent
        Task emptyPayloadTask = Task.builder()
                .id("task-002")
                .payload("{}")
                .status(TaskStatus.NEW)
                .build();

        TaskResultResponse dto = TaskResultResponse.from(emptyPayloadTask);
        assertNull(dto.algorithm());
        assertEquals("task-002", dto.taskId());
    }
}
