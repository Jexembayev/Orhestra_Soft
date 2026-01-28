package orhestra.coordinator.store;

import orhestra.coordinator.config.CoordinatorConfig;
import orhestra.coordinator.model.Task;
import orhestra.coordinator.model.TaskStatus;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JdbcTaskRepositoryTest {

    private static Database db;
    private static JdbcTaskRepository repo;

    @BeforeAll
    static void setup() {
        // Use in-memory H2 for tests
        CoordinatorConfig config = CoordinatorConfig.defaults()
                .withDatabaseUrl("jdbc:h2:mem:test-tasks;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE");
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
    void saveAndFindById() {
        Task task = Task.builder()
                .id("task-1")
                .jobId("job-1")
                .payload("{\"algorithm\":\"sphere\"}")
                .status(TaskStatus.NEW)
                .priority(5)
                .createdAt(Instant.now())
                .build();

        repo.save(task);

        Optional<Task> found = repo.findById("task-1");
        assertTrue(found.isPresent());
        assertEquals("task-1", found.get().id());
        assertEquals("job-1", found.get().jobId());
        assertEquals(TaskStatus.NEW, found.get().status());
        assertEquals(5, found.get().priority());
    }

    @Test
    void saveAll() {
        List<Task> tasks = List.of(
                Task.builder().id("t1").jobId("job-1").payload("{}").build(),
                Task.builder().id("t2").jobId("job-1").payload("{}").build(),
                Task.builder().id("t3").jobId("job-1").payload("{}").build());

        repo.saveAll(tasks);

        assertEquals(3, repo.findByJobId("job-1").size());
    }

    @Test
    void claimTasks() {
        // Create 5 tasks
        for (int i = 0; i < 5; i++) {
            repo.save(Task.builder()
                    .id("task-" + i)
                    .payload("{\"n\":" + i + "}")
                    .status(TaskStatus.NEW)
                    .build());
        }

        // Claim 3 tasks
        List<Task> claimed = repo.claimTasks("spot-1", 3);
        assertEquals(3, claimed.size());

        // Verify they're now RUNNING
        for (Task task : claimed) {
            Optional<Task> found = repo.findById(task.id());
            assertTrue(found.isPresent());
            assertEquals(TaskStatus.RUNNING, found.get().status());
            assertEquals("spot-1", found.get().assignedTo());
            assertEquals(1, found.get().attempts());
        }

        // Claim remaining should get 2
        List<Task> remaining = repo.claimTasks("spot-2", 10);
        assertEquals(2, remaining.size());
    }

    @Test
    void completeTask() {
        Task task = Task.builder()
                .id("task-complete")
                .payload("{}")
                .build();
        repo.save(task);

        // Claim it first
        repo.claimTasks("spot-1", 1);

        // Complete it
        boolean completed = repo.complete("task-complete", "spot-1", 1234, 100, -0.5, "{\"best\":[1,2,3]}");
        assertTrue(completed);

        // Verify
        Optional<Task> found = repo.findById("task-complete");
        assertTrue(found.isPresent());
        assertEquals(TaskStatus.DONE, found.get().status());
        assertEquals(1234L, found.get().runtimeMs());
        assertEquals(100, found.get().iter());
        assertEquals(-0.5, found.get().fopt(), 0.001);
    }

    @Test
    void completeTaskIdempotent() {
        Task task = Task.builder()
                .id("task-idem")
                .payload("{}")
                .build();
        repo.save(task);
        repo.claimTasks("spot-1", 1);

        // Complete once
        assertTrue(repo.complete("task-idem", "spot-1", 100, null, null, null));

        // Complete again should fail (not RUNNING anymore)
        assertFalse(repo.complete("task-idem", "spot-1", 200, null, null, null));
    }

    @Test
    void completeWrongSpot() {
        Task task = Task.builder()
                .id("task-wrong")
                .payload("{}")
                .build();
        repo.save(task);
        repo.claimTasks("spot-1", 1);

        // Try to complete from wrong spot
        assertFalse(repo.complete("task-wrong", "spot-2", 100, null, null, null));
    }

    @Test
    void failTaskWithRetry() {
        Task task = Task.builder()
                .id("task-retry")
                .payload("{}")
                .maxAttempts(3)
                .build();
        repo.save(task);
        repo.claimTasks("spot-1", 1);

        // Fail with retry
        boolean willRetry = repo.fail("task-retry", "spot-1", "timeout", true);
        assertTrue(willRetry);

        // Should be back to NEW
        Optional<Task> found = repo.findById("task-retry");
        assertTrue(found.isPresent());
        assertEquals(TaskStatus.NEW, found.get().status());
        assertNull(found.get().assignedTo());
    }

    @Test
    void failTaskPermanent() {
        Task task = Task.builder()
                .id("task-perm")
                .payload("{}")
                .maxAttempts(1)
                .build();
        repo.save(task);
        repo.claimTasks("spot-1", 1);

        // Already at max attempts (1), so should fail permanently
        boolean willRetry = repo.fail("task-perm", "spot-1", "fatal error", true);
        assertFalse(willRetry);

        // Should be FAILED
        Optional<Task> found = repo.findById("task-perm");
        assertTrue(found.isPresent());
        assertEquals(TaskStatus.FAILED, found.get().status());
    }

    @Test
    void findStuckRunning() throws InterruptedException {
        Task task = Task.builder()
                .id("task-stuck")
                .payload("{}")
                .build();
        repo.save(task);
        repo.claimTasks("spot-1", 1);

        // Query for tasks stuck since "now" - should find nothing
        List<Task> stuck = repo.findStuckRunning(Instant.now().minusSeconds(10));
        assertTrue(stuck.isEmpty());

        // Query for tasks started before "future" - should find our task
        stuck = repo.findStuckRunning(Instant.now().plusSeconds(10));
        assertEquals(1, stuck.size());
        assertEquals("task-stuck", stuck.get(0).id());
    }

    @Test
    void freeTasksForSpot() {
        for (int i = 0; i < 3; i++) {
            repo.save(Task.builder().id("task-free-" + i).payload("{}").build());
        }
        repo.claimTasks("spot-offline", 3);

        // Free tasks for offline spot
        int freed = repo.freeTasksForSpot("spot-offline");
        assertEquals(3, freed);

        // All should be NEW again
        for (int i = 0; i < 3; i++) {
            Optional<Task> found = repo.findById("task-free-" + i);
            assertTrue(found.isPresent());
            assertEquals(TaskStatus.NEW, found.get().status());
            assertNull(found.get().assignedTo());
        }
    }
}
