package orhestra.coordinator.api.internal.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import orhestra.coordinator.model.Task;

import java.util.List;

/**
 * Response DTO for claimed tasks.
 * POST /internal/v1/tasks/claim
 */
public record ClaimTasksResponse(
        @JsonProperty("tasks") List<ClaimedTask> tasks) {
    /**
     * A single claimed task with its payload.
     */
    public record ClaimedTask(
            @JsonProperty("taskId") String taskId,
            @JsonRawValue @JsonProperty("payload") String payload // Raw JSON, not re-serialized
    ) {
        public static ClaimedTask from(Task task) {
            return new ClaimedTask(task.id(), task.payload());
        }
    }

    /** Create response from domain models */
    public static ClaimTasksResponse from(List<Task> tasks) {
        List<ClaimedTask> claimed = tasks.stream()
                .map(ClaimedTask::from)
                .toList();
        return new ClaimTasksResponse(claimed);
    }

    /** Empty response (no tasks available) */
    public static ClaimTasksResponse empty() {
        return new ClaimTasksResponse(List.of());
    }

    /** Check if any tasks were claimed */
    public boolean hasTasks() {
        return tasks != null && !tasks.isEmpty();
    }
}
