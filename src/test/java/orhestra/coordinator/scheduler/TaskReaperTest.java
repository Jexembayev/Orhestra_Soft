package orhestra.coordinator.scheduler;

import orhestra.coordinator.config.CoordinatorConfig;
import orhestra.coordinator.model.Task;
import orhestra.coordinator.model.TaskStatus;
import orhestra.coordinator.store.Database;
import orhestra.coordinator.store.JdbcTaskRepository;
import org.junit.jupiter.api.*;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TaskReaper functionality.
 */
class TaskReaperTest {

    private static Database db;
    private static JdbcTaskRepository repo;
    private static CoordinatorConfig config;

    @BeforeAll
    static void setup() {
        // Use short stuck threshold for testing
        config = CoordinatorConfig.defaults()
                .withDatabaseUrl("jdbc:h2:mem:test-reaper;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE")
                .withTaskStuckThreshold(Duration.ofMillis(100)); // 100ms threshold for fast tests

        db = new Database(config);
        repo = new JdbcTaskRepository(db);
    }

    @AfterAll
    static void teardown() {
        if (db != null)
            db.close();
    }

    @BeforeEach
    void cleanTasks() throws Exception {
        try (var conn = db.getConnection();
                var st = conn.createStatement()) {
            st.execute("DELETE FROM tasks");
            conn.commit();
        }
    }

    @Test
    void reapsStuckTaskWithRetryAvailable() throws InterruptedException {
        // Create a task with retries available
        Task task = Task.builder()
                .id("task-stuck-retry")
                .payload("{\"test\":true}")
                .status(TaskStatus.NEW)
                .maxAttempts(3)
                .build();
        repo.save(task);

        // Claim it (will set to RUNNING with attempts=1)
        repo.claimTasks("spot-1", 1);

        // Wait for it to become "stuck" (longer than threshold)
        Thread.sleep(150);

        // Run the reaper
        TaskReaper reaper = new TaskReaper(repo, config);
        int reaped = reaper.reapStuckTasks();

        // Should have been reset for retry
        assertEquals(1, reaped);

        // Task should now be NEW again
        Task updated = repo.findById("task-stuck-retry").orElseThrow();
        assertEquals(TaskStatus.NEW, updated.status());
        assertNull(updated.assignedTo());
    }

    @Test
    void reapsStuckTaskWithNoRetryLeft() throws InterruptedException {
        // Create a task with NO retries available (maxAttempts=1)
        Task task = Task.builder()
                .id("task-stuck-noretry")
                .payload("{\"test\":true}")
                .status(TaskStatus.NEW)
                .maxAttempts(1)
                .build();
        repo.save(task);

        // Claim it (attempts becomes 1, maxAttempts is 1 -> no retry available)
        repo.claimTasks("spot-1", 1);

        // Wait for it to become "stuck"
        Thread.sleep(150);

        // Run the reaper
        TaskReaper reaper = new TaskReaper(repo, config);
        int reaped = reaper.reapStuckTasks();

        // Should have been marked as failed
        assertEquals(1, reaped);

        // Task should be FAILED
        Task updated = repo.findById("task-stuck-noretry").orElseThrow();
        assertEquals(TaskStatus.FAILED, updated.status());
        assertNotNull(updated.errorMessage());
        assertTrue(updated.errorMessage().contains("max attempts exceeded"));
    }

    @Test
    void doesNotReapRecentlyStartedTasks() throws InterruptedException {
        // Create and claim a task
        Task task = Task.builder()
                .id("task-recent")
                .payload("{}")
                .status(TaskStatus.NEW)
                .build();
        repo.save(task);
        repo.claimTasks("spot-1", 1);

        // Don't wait - run reaper immediately
        TaskReaper reaper = new TaskReaper(repo, config);
        int reaped = reaper.reapStuckTasks();

        // Should not be reaped (not stuck yet)
        assertEquals(0, reaped);

        // Task should still be RUNNING
        Task updated = repo.findById("task-recent").orElseThrow();
        assertEquals(TaskStatus.RUNNING, updated.status());
    }

    @Test
    void doesNotReapCompletedTasks() throws InterruptedException {
        // Create and complete a task
        Task task = Task.builder()
                .id("task-done")
                .payload("{}")
                .status(TaskStatus.NEW)
                .build();
        repo.save(task);
        repo.claimTasks("spot-1", 1);
        repo.complete("task-done", "spot-1", 100, 50, -1.0, "{}");

        // Wait past the threshold
        Thread.sleep(150);

        // Run the reaper
        TaskReaper reaper = new TaskReaper(repo, config);
        int reaped = reaper.reapStuckTasks();

        // Should not be reaped (already DONE)
        assertEquals(0, reaped);

        // Task should still be DONE
        Task updated = repo.findById("task-done").orElseThrow();
        assertEquals(TaskStatus.DONE, updated.status());
    }

    @Test
    void reapsMultipleStuckTasks() throws InterruptedException {
        // Create and claim 3 tasks
        for (int i = 0; i < 3; i++) {
            Task task = Task.builder()
                    .id("task-multi-" + i)
                    .payload("{}")
                    .status(TaskStatus.NEW)
                    .maxAttempts(3)
                    .build();
            repo.save(task);
        }
        repo.claimTasks("spot-1", 3);

        // Wait for them to become stuck
        Thread.sleep(150);

        // Run the reaper
        TaskReaper reaper = new TaskReaper(repo, config);
        int reaped = reaper.reapStuckTasks();

        // All 3 should be reaped
        assertEquals(3, reaped);

        // All should be NEW again
        for (int i = 0; i < 3; i++) {
            Task updated = repo.findById("task-multi-" + i).orElseThrow();
            assertEquals(TaskStatus.NEW, updated.status());
        }
    }
}
