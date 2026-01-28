package orhestra.coordinator.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import orhestra.coordinator.model.Spot;

import java.time.Instant;

/**
 * Response DTO for SPOT node information.
 * GET /api/v1/spots
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpotInfoResponse(
        @JsonProperty("spotId") String spotId,
        @JsonProperty("status") String status,
        @JsonProperty("ipAddress") String ipAddress,
        @JsonProperty("cpuLoad") double cpuLoad,
        @JsonProperty("runningTasks") int runningTasks,
        @JsonProperty("totalCores") int totalCores,
        @JsonProperty("lastHeartbeat") Instant lastHeartbeat) {
    /** Create response from domain model */
    public static SpotInfoResponse from(Spot spot) {
        return new SpotInfoResponse(
                spot.id(),
                spot.status().name(),
                spot.ipAddress(),
                spot.cpuLoad(),
                spot.runningTasks(),
                spot.totalCores(),
                spot.lastHeartbeat());
    }
}
