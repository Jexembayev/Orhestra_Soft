package orhestra.coordinator.api.internal.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for claiming tasks.
 * POST /internal/v1/tasks/claim
 */
public record ClaimTasksRequest(
        @JsonProperty("spotId") String spotId,
        @JsonProperty("maxTasks") int maxTasks) {
    /** Default max tasks if not specified */
    public static final int DEFAULT_MAX_TASKS = 1;

    /** Maximum tasks allowed per claim */
    public static final int MAX_ALLOWED = 10;

    public void validate() {
        if (spotId == null || spotId.isBlank()) {
            throw new IllegalArgumentException("spotId is required");
        }
        if (maxTasks <= 0) {
            throw new IllegalArgumentException("maxTasks must be positive");
        }
        if (maxTasks > MAX_ALLOWED) {
            throw new IllegalArgumentException("maxTasks cannot exceed " + MAX_ALLOWED);
        }
    }

    /** Create request with defaults */
    public static ClaimTasksRequest of(String spotId) {
        return new ClaimTasksRequest(spotId, DEFAULT_MAX_TASKS);
    }
}
