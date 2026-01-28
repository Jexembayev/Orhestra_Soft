package orhestra.coordinator.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TaskTest {

    @Test
    void buildMinimalTask() {
        Task task = Task.builder()
                .id("task-1")
                .payload("{\"algorithm\":\"sphere\"}")
                .build();

        assertEquals("task-1", task.id());
        assertEquals("{\"algorithm\":\"sphere\"}", task.payload());
        assertEquals(TaskStatus.NEW, task.status());
        assertNull(task.assignedTo());
        assertEquals(0, task.priority());
        assertEquals(0, task.attempts());
        assertEquals(3, task.maxAttempts());
    }

    @Test
    void buildFullTask() {
        Instant now = Instant.now();

        Task task = Task.builder()
                .id("task-2")
                .jobId("job-1")
                .payload("{}")
                .status(TaskStatus.RUNNING)
                .assignedTo("spot-1")
                .priority(10)
                .attempts(1)
                .maxAttempts(5)
                .createdAt(now)
                .startedAt(now)
                .build();

        assertEquals("task-2", task.id());
        assertEquals("job-1", task.jobId());
        assertEquals(TaskStatus.RUNNING, task.status());
        assertEquals("spot-1", task.assignedTo());
        assertEquals(10, task.priority());
        assertEquals(1, task.attempts());
        assertEquals(5, task.maxAttempts());
    }

    @Test
    void canRetry() {
        Task retriable = Task.builder()
                .id("t1")
                .payload("{}")
                .attempts(2)
                .maxAttempts(3)
                .build();
        assertTrue(retriable.canRetry());

        Task exhausted = Task.builder()
                .id("t2")
                .payload("{}")
                .attempts(3)
                .maxAttempts(3)
                .build();
        assertFalse(exhausted.canRetry());
    }

    @Test
    void isTerminal() {
        Task newTask = Task.builder().id("t1").payload("{}").status(TaskStatus.NEW).build();
        assertFalse(newTask.isTerminal());

        Task runningTask = Task.builder().id("t2").payload("{}").status(TaskStatus.RUNNING).build();
        assertFalse(runningTask.isTerminal());

        Task doneTask = Task.builder().id("t3").payload("{}").status(TaskStatus.DONE).build();
        assertTrue(doneTask.isTerminal());

        Task failedTask = Task.builder().id("t4").payload("{}").status(TaskStatus.FAILED).build();
        assertTrue(failedTask.isTerminal());
    }

    @Test
    void toBuilder() {
        Task original = Task.builder()
                .id("task-1")
                .payload("{}")
                .status(TaskStatus.NEW)
                .build();

        Task modified = original.toBuilder()
                .status(TaskStatus.RUNNING)
                .assignedTo("spot-1")
                .attempts(1)
                .build();

        // Original unchanged
        assertEquals(TaskStatus.NEW, original.status());
        assertNull(original.assignedTo());

        // Modified has new values
        assertEquals(TaskStatus.RUNNING, modified.status());
        assertEquals("spot-1", modified.assignedTo());
        assertEquals(1, modified.attempts());
        assertEquals("task-1", modified.id()); // ID preserved
    }

    @Test
    void equality() {
        Task t1 = Task.builder().id("task-1").payload("{}").build();
        Task t2 = Task.builder().id("task-1").payload("{different}").build();
        Task t3 = Task.builder().id("task-2").payload("{}").build();

        assertEquals(t1, t2); // Same ID
        assertNotEquals(t1, t3); // Different ID
        assertEquals(t1.hashCode(), t2.hashCode());
    }
}
