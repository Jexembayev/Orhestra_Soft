package orhestra.coordinator.integration;

import orhestra.coordinator.api.v1.dto.TaskResultResponse;
import orhestra.coordinator.config.CoordinatorConfig;
import orhestra.coordinator.config.Dependencies;
import orhestra.coordinator.model.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the full job workflow:
 * 1. Create a job with tasks
 * 2. Register a SPOT
 * 3. Claim tasks
 * 4. Complete tasks (including idempotent retry)
 * 5. Fail tasks (including idempotent retry)
 * 6. Verify job completion
 */
class FullFlowIntegrationTest {

        private Dependencies deps;

        @BeforeEach
        void setUp() {
                CoordinatorConfig config = CoordinatorConfig.defaults()
                                .withDatabaseUrl("jdbc:h2:mem:test-flow-" + System.nanoTime()
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
        @DisplayName("Full workflow: create job, claim tasks, complete, verify")
        void testFullJobWorkflow() {
                // 1. Create job with 3 tasks (full payload format with agents + dimension)
                Job job = deps.jobService().createJob(
                                "/path/to/test.jar",
                                "com.example.Main",
                                "{\"test\": true}",
                                List.of(
                                                "{\"alg\":\"PSO\",\"iterations\":{\"max\":100},\"agents\":10,\"dimension\":2}",
                                                "{\"alg\":\"GA\",\"iterations\":{\"max\":200},\"agents\":25,\"dimension\":5}",
                                                "{\"alg\":\"DE\",\"iterations\":{\"max\":150},\"agents\":50,\"dimension\":3}"));

                assertNotNull(job);
                assertEquals("/path/to/test.jar", job.jarPath());
                assertEquals(3, job.totalTasks());
                assertEquals(JobStatus.PENDING, job.status());

                // 2. Register a SPOT
                String spotId = deps.spotService().registerSpot("192.168.1.100");
                assertNotNull(spotId);

                // 3. Claim tasks
                List<Task> claimed = deps.taskService().claimTasks(spotId, 10);
                assertEquals(3, claimed.size());

                // Verify all tasks are now RUNNING
                for (Task t : claimed) {
                        assertEquals(TaskStatus.RUNNING, t.status());
                        assertEquals(spotId, t.assignedTo());
                }

                // 4. Complete first task
                Task task1 = claimed.get(0);
                TaskCompleteResult result1 = deps.taskService().completeTaskIdempotent(
                                task1.id(), spotId, 1500, 100, 0.001, "{\"result\":\"success\"}");
                assertEquals(TaskCompleteResult.COMPLETED, result1);

                // 5. Test idempotent completion (same task again)
                TaskCompleteResult result1Again = deps.taskService().completeTaskIdempotent(
                                task1.id(), spotId, 1500, 100, 0.001, "{\"result\":\"success\"}");
                assertEquals(TaskCompleteResult.ALREADY_DONE, result1Again);

                // 6. Complete second task
                Task task2 = claimed.get(1);
                TaskCompleteResult result2 = deps.taskService().completeTaskIdempotent(
                                task2.id(), spotId, 2000, 200, 0.002, "{\"result\":\"success\"}");
                assertEquals(TaskCompleteResult.COMPLETED, result2);

                // 7. Fail third task (retriable)
                Task task3 = claimed.get(2);
                TaskFailResult failResult = deps.taskService().failTaskIdempotent(
                                task3.id(), spotId, "Timeout error", true);
                assertEquals(TaskFailResult.RETRIED, failResult);

                // 8. Verify task3 is back to NEW (ready for retry)
                Task task3After = deps.taskRepository().findById(task3.id()).orElseThrow();
                assertEquals(TaskStatus.NEW, task3After.status());
                assertNull(task3After.assignedTo());

                // 9. Claim the retried task
                List<Task> claimed2 = deps.taskService().claimTasks(spotId, 5);
                assertEquals(1, claimed2.size());
                assertEquals(task3.id(), claimed2.get(0).id());

                // 10. Complete the retried task
                TaskCompleteResult result3 = deps.taskService().completeTaskIdempotent(
                                task3.id(), spotId, 1200, 150, 0.0015, "{\"result\":\"retry_success\"}");
                assertEquals(TaskCompleteResult.COMPLETED, result3);

                // 11. No more tasks to claim
                List<Task> claimed3 = deps.taskService().claimTasks(spotId, 10);
                assertTrue(claimed3.isEmpty());

                // 12. Verify all tasks are DONE
                List<Task> allTasks = deps.taskRepository().findByJobId(job.id());
                assertEquals(3, allTasks.size());
                for (Task t : allTasks) {
                        assertEquals(TaskStatus.DONE, t.status());
                        assertNotNull(t.result());
                }

                // 13. Verify TaskResultResponse DTO populates payload fields
                for (Task t : allTasks) {
                        TaskResultResponse dto = TaskResultResponse.from(t);
                        assertNotNull(dto.algorithm(), "algorithm should be parsed from payload");
                        assertNotNull(dto.iterations(), "iterations should be parsed from payload");
                        assertNotNull(dto.agents(), "agents should be parsed from payload");
                        assertNotNull(dto.dimension(), "dimension should be parsed from payload");
                        assertNotNull(dto.runtimeMs(), "runtimeMs should be set");
                        assertNotNull(dto.fopt(), "fopt should be set");
                        assertEquals("DONE", dto.status());
                }
        }

        @Test
        @DisplayName("Idempotent fail: permanent failure after max attempts")
        void testPermanentFailure() {
                // Create job with 1 task (maxAttempts = 1)
                CoordinatorConfig config = CoordinatorConfig.defaults()
                                .withDatabaseUrl("jdbc:h2:mem:test-fail-" + System.nanoTime()
                                                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE")
                                .withMaxAttempts(1);
                Dependencies deps2 = Dependencies.create(config);

                try {
                        Job job = deps2.jobService().createJob(
                                        "/path/to/test.jar",
                                        "com.example.Main",
                                        "{}",
                                        List.of("{\"test\":true}"));

                        String spotId = deps2.spotService().registerSpot("192.168.1.101");
                        List<Task> claimed = deps2.taskService().claimTasks(spotId, 10);
                        assertEquals(1, claimed.size());

                        // Fail the task (non-retriable because maxAttempts = 1 and task already has 1
                        // attempt)
                        TaskFailResult failResult = deps2.taskService().failTaskIdempotent(
                                        claimed.get(0).id(), spotId, "Fatal error", true);
                        assertEquals(TaskFailResult.FAILED, failResult);

                        // Verify task is FAILED
                        Task task = deps2.taskRepository().findById(claimed.get(0).id()).orElseThrow();
                        assertEquals(TaskStatus.FAILED, task.status());

                        // Idempotent retry should return ALREADY_TERMINAL
                        TaskFailResult failAgain = deps2.taskService().failTaskIdempotent(
                                        claimed.get(0).id(), spotId, "Another error", true);
                        assertEquals(TaskFailResult.ALREADY_TERMINAL, failAgain);
                } finally {
                        deps2.close();
                }
        }

        @Test
        @DisplayName("Wrong spot cannot complete task")
        void testWrongSpotCannotComplete() {
                // Create job
                Job job = deps.jobService().createJob(
                                "/path/to/test.jar",
                                "com.example.Main",
                                "{}",
                                List.of("{\"test\":true}"));

                // Register two SPOTs
                String spot1 = deps.spotService().registerSpot("192.168.1.1");
                String spot2 = deps.spotService().registerSpot("192.168.1.2");

                // Spot1 claims task
                List<Task> claimed = deps.taskService().claimTasks(spot1, 10);
                assertEquals(1, claimed.size());

                // Spot2 tries to complete - should return WRONG_SPOT
                TaskCompleteResult result = deps.taskService().completeTaskIdempotent(
                                claimed.get(0).id(), spot2, 1000, 100, 0.001, "{}");
                assertEquals(TaskCompleteResult.WRONG_SPOT, result);

                // Task should still be RUNNING assigned to spot1
                Task task = deps.taskRepository().findById(claimed.get(0).id()).orElseThrow();
                assertEquals(TaskStatus.RUNNING, task.status());
                assertEquals(spot1, task.assignedTo());
        }

        @Test
        @DisplayName("Task not found returns proper result")
        void testTaskNotFound() {
                String spotId = deps.spotService().registerSpot("192.168.1.1");

                TaskCompleteResult completeResult = deps.taskService().completeTaskIdempotent(
                                "nonexistent-task", spotId, 1000, 100, 0.001, "{}");
                assertEquals(TaskCompleteResult.NOT_FOUND, completeResult);

                TaskFailResult failResult = deps.taskService().failTaskIdempotent(
                                "nonexistent-task", spotId, "error", true);
                assertEquals(TaskFailResult.NOT_FOUND, failResult);
        }
}
