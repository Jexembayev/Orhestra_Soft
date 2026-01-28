package orhestra.coordinator.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for health check.
 * GET /api/v1/health
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthResponse(
        @JsonProperty("status") String status,
        @JsonProperty("database") String database,
        @JsonProperty("uptime") String uptime,
        @JsonProperty("version") String version,
        @JsonProperty("activeSpots") Integer activeSpots,
        @JsonProperty("pendingTasks") Integer pendingTasks,
        @JsonProperty("runningTasks") Integer runningTasks) {
    public static HealthResponse healthy(String uptime, String version, int activeSpots, int pendingTasks,
            int runningTasks) {
        return new HealthResponse("healthy", "ok", uptime, version, activeSpots, pendingTasks, runningTasks);
    }

    public static HealthResponse unhealthy(String database) {
        return new HealthResponse("unhealthy", database, null, null, null, null, null);
    }
}
