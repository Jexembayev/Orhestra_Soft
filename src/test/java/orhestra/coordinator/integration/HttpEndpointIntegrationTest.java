package orhestra.coordinator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import orhestra.coordinator.config.CoordinatorConfig;
import orhestra.coordinator.server.CoordinatorNettyServer;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that hits actual HTTP endpoints.
 * Tests the full flow through Netty server to verify job counters are updated.
 */
class HttpEndpointIntegrationTest {

        private static final ObjectMapper MAPPER = new ObjectMapper();
        private static final int TEST_PORT = 18080;

        private HttpClient httpClient;

        @BeforeEach
        void setUp() throws Exception {
                // Stop any existing server instance
                if (CoordinatorNettyServer.isRunning()) {
                        CoordinatorNettyServer.stop();
                }

                CoordinatorConfig config = CoordinatorConfig.defaults()
                                .withDatabaseUrl("jdbc:h2:mem:test-http-" + System.nanoTime()
                                                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE")
                                .withMaxAttempts(3);

                CoordinatorNettyServer.start(TEST_PORT, config);

                // Wait for server to be ready
                TimeUnit.MILLISECONDS.sleep(200);

                httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .build();
        }

        @AfterEach
        void tearDown() {
                CoordinatorNettyServer.stop();
        }

        @Test
        @DisplayName("Full HTTP flow: create job, claim, complete, verify job counters")
        void testJobCountersSyncViaHttp() throws Exception {
                String baseUrl = "http://localhost:" + TEST_PORT;

                // 1. Create a job via POST /api/v1/jobs
                String createJobBody = """
                                {
                                    "jarPath": "/test/optimization.jar",
                                    "mainClass": "com.test.Main",
                                    "algorithms": ["PSO"],
                                    "iterations": {"min": 100, "max": 100, "step": 1},
                                    "agents": {"min": 10, "max": 10, "step": 1},
                                    "dimension": {"min": 2, "max": 2, "step": 1}
                                }
                                """;

                HttpResponse<String> createResponse = httpClient.send(
                                HttpRequest.newBuilder()
                                                .uri(URI.create(baseUrl + "/api/v1/jobs"))
                                                .header("Content-Type", "application/json")
                                                .POST(HttpRequest.BodyPublishers.ofString(createJobBody))
                                                .build(),
                                HttpResponse.BodyHandlers.ofString());

                assertEquals(201, createResponse.statusCode(),
                                "Create job should return 201 CREATED. Body: " + createResponse.body());
                JsonNode createResult = MAPPER.readTree(createResponse.body());
                String jobId = createResult.get("jobId").asText();
                assertNotNull(jobId);

                // Verify initial job status
                JsonNode initialJob = getJob(baseUrl, jobId);
                assertEquals("PENDING", initialJob.get("status").asText());
                assertEquals(0, initialJob.get("completedTasks").asInt());
                assertEquals(1, initialJob.get("totalTasks").asInt());

                // 2. Register a spot via heartbeat
                String spotId = "spot-test-" + System.nanoTime();
                String heartbeatBody = String.format("""
                                {
                                    "spotId": "%s",
                                    "cpuLoad": 25.0,
                                    "runningTasks": 0,
                                    "totalCores": 8
                                }
                                """, spotId);

                HttpResponse<String> heartbeatResponse = httpClient.send(
                                HttpRequest.newBuilder()
                                                .uri(URI.create(baseUrl + "/internal/v1/heartbeat"))
                                                .header("Content-Type", "application/json")
                                                .POST(HttpRequest.BodyPublishers.ofString(heartbeatBody))
                                                .build(),
                                HttpResponse.BodyHandlers.ofString());

                assertEquals(200, heartbeatResponse.statusCode(),
                                "Heartbeat should succeed. Body: " + heartbeatResponse.body());

                // 3. Claim tasks
                String claimBody = String.format("""
                                {
                                    "spotId": "%s",
                                    "maxTasks": 5
                                }
                                """, spotId);

                HttpResponse<String> claimResponse = httpClient.send(
                                HttpRequest.newBuilder()
                                                .uri(URI.create(baseUrl + "/internal/v1/tasks/claim"))
                                                .header("Content-Type", "application/json")
                                                .POST(HttpRequest.BodyPublishers.ofString(claimBody))
                                                .build(),
                                HttpResponse.BodyHandlers.ofString());

                assertEquals(200, claimResponse.statusCode(), "Claim should succeed. Body: " + claimResponse.body());
                JsonNode claimResult = MAPPER.readTree(claimResponse.body());
                assertTrue(claimResult.has("tasks"));
                assertEquals(1, claimResult.get("tasks").size());
                String taskId = claimResult.get("tasks").get(0).get("taskId").asText();

                // 4. Complete the task
                String completeBody = String.format("""
                                {
                                    "spotId": "%s",
                                    "runtimeMs": 1500,
                                    "iter": 100,
                                    "fopt": 0.001,
                                    "unknownField": "should be ignored"
                                }
                                """, spotId);

                HttpResponse<String> completeResponse = httpClient.send(
                                HttpRequest.newBuilder()
                                                .uri(URI.create(baseUrl + "/internal/v1/tasks/" + taskId + "/complete"))
                                                .header("Content-Type", "application/json")
                                                .POST(HttpRequest.BodyPublishers.ofString(completeBody))
                                                .build(),
                                HttpResponse.BodyHandlers.ofString());

                assertEquals(200, completeResponse.statusCode(),
                                "Complete should succeed. Body: " + completeResponse.body());
                JsonNode completeResult = MAPPER.readTree(completeResponse.body());
                assertTrue(completeResult.get("success").asBoolean());

                // 5. VERIFY job counters are correctly updated via GET /api/v1/jobs/{jobId}
                JsonNode updatedJob = getJob(baseUrl, jobId);
                assertEquals(1, updatedJob.get("completedTasks").asInt(),
                                "completedTasks should be 1 after task completion. Full job: " + updatedJob);
                assertEquals(0, updatedJob.get("failedTasks").asInt());
                assertEquals("COMPLETED", updatedJob.get("status").asText(),
                                "Job status should be COMPLETED when all tasks done. Full job: " + updatedJob);

                // 6. Idempotent: Complete again, counters should stay at 1
                HttpResponse<String> completeAgain = httpClient.send(
                                HttpRequest.newBuilder()
                                                .uri(URI.create(baseUrl + "/internal/v1/tasks/" + taskId + "/complete"))
                                                .header("Content-Type", "application/json")
                                                .POST(HttpRequest.BodyPublishers.ofString(completeBody))
                                                .build(),
                                HttpResponse.BodyHandlers.ofString());

                assertEquals(200, completeAgain.statusCode());
                JsonNode completeAgainResult = MAPPER.readTree(completeAgain.body());
                assertTrue(completeAgainResult.get("success").asBoolean());
                assertEquals("already completed", completeAgainResult.path("message").asText());

                // Verify job counters unchanged after idempotent call
                JsonNode finalJob = getJob(baseUrl, jobId);
                assertEquals(1, finalJob.get("completedTasks").asInt(),
                                "completedTasks should still be 1 after idempotent call (not 2)");
                assertEquals("COMPLETED", finalJob.get("status").asText());

                // 7. Verify results endpoint also shows task as DONE
                HttpResponse<String> resultsResponse = httpClient.send(
                                HttpRequest.newBuilder()
                                                .uri(URI.create(baseUrl + "/api/v1/jobs/" + jobId + "/results"))
                                                .GET()
                                                .build(),
                                HttpResponse.BodyHandlers.ofString());

                assertEquals(200, resultsResponse.statusCode(),
                                "Results should succeed. Body: " + resultsResponse.body());
                JsonNode results = MAPPER.readTree(resultsResponse.body());
                assertEquals(1, results.get("totalCompleted").asInt());
                assertEquals(1, results.get("results").size());
                assertEquals("DONE", results.get("results").get(0).get("status").asText());
        }

        private JsonNode getJob(String baseUrl, String jobId) throws Exception {
                HttpResponse<String> response = httpClient.send(
                                HttpRequest.newBuilder()
                                                .uri(URI.create(baseUrl + "/api/v1/jobs/" + jobId))
                                                .GET()
                                                .build(),
                                HttpResponse.BodyHandlers.ofString());

                assertEquals(200, response.statusCode(),
                                "GET job should succeed. Body: " + response.body());
                return MAPPER.readTree(response.body());
        }
}
