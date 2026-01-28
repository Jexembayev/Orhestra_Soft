package orhestra.coordinator.api.internal.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for reporting task failure.
 * POST /internal/v1/tasks/{taskId}/fail
 */
public record TaskFailRequest(
        @JsonProperty("spotId") String spotId,
        @JsonProperty("error") String error,
        @JsonProperty("retriable") boolean retriable) {
    /** Default: failures are retriable unless explicitly marked otherwise */
    public static final boolean DEFAULT_RETRIABLE = true;

    public void validate() {
        if (spotId == null || spotId.isBlank()) {
            throw new IllegalArgumentException("spotId is required");
        }
    }

    /** Create with defaults */
    public static TaskFailRequest of(String spotId, String error) {
        return new TaskFailRequest(spotId, error, DEFAULT_RETRIABLE);
    }

    /** Create non-retriable failure */
    public static TaskFailRequest permanent(String spotId, String error) {
        return new TaskFailRequest(spotId, error, false);
    }
}
