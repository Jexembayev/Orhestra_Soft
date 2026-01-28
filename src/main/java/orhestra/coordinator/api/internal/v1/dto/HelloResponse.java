package orhestra.coordinator.api.internal.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for SPOT registration.
 * POST /internal/v1/hello
 */
public record HelloResponse(
        @JsonProperty("spotId") String spotId,
        @JsonProperty("coordinatorVersion") String coordinatorVersion) {
    public static final String VERSION = "2.0.0";

    public static HelloResponse create(String spotId) {
        return new HelloResponse(spotId, VERSION);
    }
}
