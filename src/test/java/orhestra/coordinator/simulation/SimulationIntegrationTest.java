package orhestra.coordinator.simulation;

import orhestra.coordinator.config.CoordinatorConfig;
import orhestra.coordinator.config.Dependencies;
import orhestra.coordinator.model.Job;
import orhestra.coordinator.model.Task;
import orhestra.coordinator.model.TaskStatus;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for SPOT simulation.
 * Creates a job, starts simulated workers, waits for all tasks to complete.
 */
class SimulationIntegrationTest {

    private Dependencies deps;

    @BeforeEach
    void setUp() {
        CoordinatorConfig config = CoordinatorConfig.defaults()
                .withDatabaseUrl("jdbc:h2:mem:test-sim-" + System.nanoTime()
                        + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE")
                .withMaxAttempts(3);
        deps = Dependencies.create(config);
    }

    @AfterEach
    void tearDown() {
        if (deps != null) {
            deps.close();
        }
    }

    @Test
    @DisplayName("Simulation E2E: 5 workers complete 20 tasks")
    void simulationCompletesAllTasks() throws InterruptedException {
        int totalTasks = 20;
        int workers = 5;

        // 1. Create a job with N tasks
        List<String> payloads = new ArrayList<>();
        for (int i = 0; i < totalTasks; i++) {
            payloads.add("{\"alg\":\"SIM_TEST\",\"iterations\":{\"max\":10},\"agents\":2,\"dimension\":2}");
        }

        Job job = deps.jobService().createJob(
                "/path/to/sim.jar",
                "com.example.SimMain",
                "{}",
                payloads);

        assertEquals(totalTasks, job.totalTasks());

        // 2. Start simulation with fast delays
        SimulationService sim = new SimulationService(deps.spotService(), deps.taskService());
        sim.start(workers, 10, 50, 0.0); // Very fast for test

        // 3. Wait for all tasks to finish (max 15 seconds)
        long deadline = System.currentTimeMillis() + 15_000;
        int done = 0, failed = 0;

        while (System.currentTimeMillis() < deadline) {
            List<Task> tasks = deps.taskRepository().findByJobId(job.id());
            done = (int) tasks.stream().filter(t -> t.status() == TaskStatus.DONE).count();
            failed = (int) tasks.stream().filter(t -> t.status() == TaskStatus.FAILED).count();

            if (done + failed >= totalTasks)
                break;
            Thread.sleep(200);
        }

        // 4. Stop simulation
        sim.stop();

        // 5. Assert all tasks completed
        assertEquals(totalTasks, done + failed,
                "All tasks should be done or failed. done=" + done + " failed=" + failed);
        assertEquals(totalTasks, done,
                "With failRate=0, all tasks should be DONE");

        // 6. No tasks stuck in RUNNING
        List<Task> finalTasks = deps.taskRepository().findByJobId(job.id());
        long running = finalTasks.stream().filter(t -> t.status() == TaskStatus.RUNNING).count();
        assertEquals(0, running, "No tasks should be stuck in RUNNING after simulation");
    }

    @Test
    @DisplayName("Simulation with fail rate produces some failures")
    void simulationWithFailRate() throws InterruptedException {
        int totalTasks = 30;
        int workers = 5;

        List<String> payloads = new ArrayList<>();
        for (int i = 0; i < totalTasks; i++) {
            payloads.add("{\"alg\":\"FAIL_TEST\",\"iterations\":5,\"agents\":1,\"dimension\":1}");
        }

        Job job = deps.jobService().createJob("/path/to/sim.jar", "com.example.Main", "{}", payloads);

        // Start with 50% fail rate
        SimulationService sim = new SimulationService(deps.spotService(), deps.taskService());
        sim.start(workers, 10, 30, 0.5);

        // Wait for completion
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            List<Task> tasks = deps.taskRepository().findByJobId(job.id());
            long terminal = tasks.stream()
                    .filter(t -> t.status() == TaskStatus.DONE || t.status() == TaskStatus.FAILED)
                    .count();
            if (terminal >= totalTasks)
                break;
            Thread.sleep(200);
        }

        sim.stop();

        List<Task> finalTasks = deps.taskRepository().findByJobId(job.id());
        long done = finalTasks.stream().filter(t -> t.status() == TaskStatus.DONE).count();
        long failed = finalTasks.stream().filter(t -> t.status() == TaskStatus.FAILED).count();

        assertEquals(totalTasks, done + failed,
                "All tasks should be terminal. done=" + done + " failed=" + failed);
        // With 50% fail rate, we expect some failures (statistically very unlikely to
        // have 0)
        assertTrue(failed > 0, "With 50% fail rate, at least some tasks should fail");
    }
}
