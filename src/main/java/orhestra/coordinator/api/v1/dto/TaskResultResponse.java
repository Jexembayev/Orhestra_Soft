package orhestra.coordinator.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import orhestra.coordinator.model.Task;

import java.time.Instant;

/**
 * Response DTO for task result.
 * Used in JobResponse.results array.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskResultResponse(
        @JsonProperty("taskId") String taskId,
        @JsonProperty("status") String status,
        @JsonProperty("algorithm") String algorithm,
        @JsonProperty("iterations") Integer iterations,
        @JsonProperty("agents") Integer agents,
        @JsonProperty("dimension") Integer dimension,
        @JsonProperty("runtimeMs") Long runtimeMs,
        @JsonProperty("iter") Integer iter,
        @JsonProperty("fopt") Double fopt,
        @JsonProperty("assignedTo") String assignedTo,
        @JsonProperty("startedAt") Instant startedAt,
        @JsonProperty("finishedAt") Instant finishedAt,
        @JsonProperty("errorMessage") String errorMessage) {
    /** Create response from domain model */
    public static TaskResultResponse from(Task task) {
        // Parse algorithm params from payload if needed
        // For now, we return the raw data
        return new TaskResultResponse(
                task.id(),
                task.status().name(),
                null, // algorithm - parse from payload
                null, // iterations - parse from payload
                null, // agents - parse from payload
                null, // dimension - parse from payload
                task.runtimeMs(),
                task.iter(),
                task.fopt(),
                task.assignedTo(),
                task.startedAt(),
                task.finishedAt(),
                task.errorMessage());
    }

    /** Create response with parsed payload */
    public static TaskResultResponse from(Task task, String algorithm, Integer iterations, Integer agents,
            Integer dimension) {
        return new TaskResultResponse(
                task.id(),
                task.status().name(),
                algorithm,
                iterations,
                agents,
                dimension,
                task.runtimeMs(),
                task.iter(),
                task.fopt(),
                task.assignedTo(),
                task.startedAt(),
                task.finishedAt(),
                task.errorMessage());
    }
}
