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
        @JsonProperty("jobId")            String jobId,
        @JsonProperty("status")           String status,
        @JsonProperty("artifactBucket")   String artifactBucket,
        @JsonProperty("artifactKey")      String artifactKey,
        @JsonProperty("artifactEndpoint") String artifactEndpoint,
        @JsonProperty("mainClass")        String mainClass,
        @JsonProperty("totalTasks")       int totalTasks,
        @JsonProperty("completedTasks")   int completedTasks,
        @JsonProperty("failedTasks")      int failedTasks,
        @JsonProperty("createdAt")        Instant createdAt,
        @JsonProperty("startedAt")        Instant startedAt,
        @JsonProperty("finishedAt")       Instant finishedAt,
        @JsonProperty("results")          List<TaskResultResponse> results) {

    public static JobResponse from(Job job) {
        return new JobResponse(
                job.id(),
                job.status().name(),
                job.artifact().bucket(),
                job.artifact().key(),
                job.artifact().endpoint(),
                job.mainClass(),
                job.totalTasks(),
                job.completedTasks(),
                job.failedTasks(),
                job.createdAt(),
                job.startedAt(),
                job.finishedAt(),
                null
        );
    }

    public static JobResponse from(Job job, List<Task> tasks) {
        List<TaskResultResponse> results = tasks.stream()
                .map(TaskResultResponse::from)
                .toList();

        return new JobResponse(
                job.id(),
                job.status().name(),
                job.artifact().bucket(),
                job.artifact().key(),
                job.artifact().endpoint(),
                job.mainClass(),
                job.totalTasks(),
                job.completedTasks(),
                job.failedTasks(),
                job.createdAt(),
                job.startedAt(),
                job.finishedAt(),
                results);
    }

    public JobResponse compact() {
        return new JobResponse(
                jobId, status, artifactBucket, artifactKey, artifactEndpoint, mainClass,
                totalTasks, completedTasks, failedTasks,
                createdAt, startedAt, finishedAt, null);
    }
}
