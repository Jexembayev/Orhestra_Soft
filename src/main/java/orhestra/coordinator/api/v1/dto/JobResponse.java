package orhestra.coordinator.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import orhestra.coordinator.model.Job;
import orhestra.coordinator.model.Task;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for job details.
 * GET /api/v1/jobs/{jobId}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobResponse(
        @JsonProperty("jobId") String jobId,
        @JsonProperty("status") String status,
        @JsonProperty("totalTasks") int totalTasks,
        @JsonProperty("completedTasks") int completedTasks,
        @JsonProperty("failedTasks") int failedTasks,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("startedAt") Instant startedAt,
        @JsonProperty("finishedAt") Instant finishedAt,
        @JsonProperty("results") List<TaskResultResponse> results) {
    /** Create response from domain model */
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.id(),
                job.status().name(),
                job.totalTasks(),
                job.completedTasks(),
                job.failedTasks(),
                job.createdAt(),
                job.startedAt(),
                job.finishedAt(),
                null // results populated separately when needed
        );
    }

    /** Create response with results */
    public static JobResponse from(Job job, List<Task> tasks) {
        List<TaskResultResponse> results = tasks.stream()
                .map(TaskResultResponse::from)
                .toList();

        return new JobResponse(
                job.id(),
                job.status().name(),
                job.totalTasks(),
                job.completedTasks(),
                job.failedTasks(),
                job.createdAt(),
                job.startedAt(),
                job.finishedAt(),
                results);
    }

    /** Compact version for list responses */
    public JobResponse compact() {
        return new JobResponse(
                jobId, status, totalTasks, completedTasks, failedTasks,
                createdAt, startedAt, finishedAt, null);
    }
}
