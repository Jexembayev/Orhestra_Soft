package orhestra.coordinator.service;

import orhestra.coordinator.api.internal.v1.dto.ClaimTasksResponse;
import orhestra.coordinator.config.CoordinatorConfig;
import orhestra.coordinator.model.Task;
import orhestra.coordinator.model.TaskCompleteResult;
import orhestra.coordinator.model.TaskFailResult;
import orhestra.coordinator.model.TaskStatus;
import orhestra.coordinator.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for task operations.
 * Contains business logic for task claiming, completion, and lifecycle
 * management.
 */
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final CoordinatorConfig config;

    public TaskService(TaskRepository taskRepository, CoordinatorConfig config) {
        this.taskRepository = taskRepository;
        this.config = config;
    }

    /**
     * Claim tasks for a SPOT node.
     * Atomically assigns up to maxTasks to the SPOT.
     */
    public List<Task> claimTasks(String spotId, int maxTasks) {
        if (spotId == null || spotId.isBlank()) {
            throw new IllegalArgumentException("spotId is required");
        }
        if (maxTasks <= 0) {
            throw new IllegalArgumentException("maxTasks must be positive");
        }

        return taskRepository.claimTasks(spotId, Math.min(maxTasks, 10));
    }

    /**
     * Complete a task successfully.
     * 
     * @return true if completed, false if not found or not assigned to spot
     */
    public boolean completeTask(String taskId, String spotId, long runtimeMs, Integer iter, Double fopt,
            String result) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        if (spotId == null || spotId.isBlank()) {
            throw new IllegalArgumentException("spotId is required");
        }

        boolean completed = taskRepository.complete(taskId, spotId, runtimeMs, iter, fopt, result);

        if (completed) {
            log.info("Task {} completed by spot {} in {}ms", taskId, spotId, runtimeMs);
        } else {
            log.warn("Failed to complete task {} by spot {} - not found or not assigned", taskId, spotId);
        }

        return completed;
    }

    /**
     * Complete a task successfully with idempotent result.
     * Returns detailed result for HTTP response handling.
     */
    public TaskCompleteResult completeTaskIdempotent(String taskId, String spotId, long runtimeMs, Integer iter,
            Double fopt, String result) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        if (spotId == null || spotId.isBlank()) {
            throw new IllegalArgumentException("spotId is required");
        }

        TaskCompleteResult res = taskRepository.completeIdempotent(taskId, spotId, runtimeMs, iter, fopt, result);

        if (res == TaskCompleteResult.COMPLETED) {
            log.info("Task {} completed by spot {} in {}ms", taskId, spotId, runtimeMs);
        } else if (res == TaskCompleteResult.ALREADY_DONE) {
            log.debug("Task {} already complete (idempotent)", taskId);
        } else {
            log.warn("Failed to complete task {} by spot {} - result: {}", taskId, spotId, res);
        }

        return res;
    }

    /**
     * Report task failure.
     * 
     * @return true if task will be retried, false if permanently failed
     */
    public boolean failTask(String taskId, String spotId, String errorMessage, boolean retriable) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        if (spotId == null || spotId.isBlank()) {
            throw new IllegalArgumentException("spotId is required");
        }

        return taskRepository.fail(taskId, spotId, errorMessage, retriable);
    }

    /**
     * Report task failure with idempotent result.
     * Returns detailed result for HTTP response handling.
     */
    public TaskFailResult failTaskIdempotent(String taskId, String spotId, String errorMessage, boolean retriable) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        if (spotId == null || spotId.isBlank()) {
            throw new IllegalArgumentException("spotId is required");
        }

        TaskFailResult res = taskRepository.failIdempotent(taskId, spotId, errorMessage, retriable);

        if (res == TaskFailResult.RETRIED) {
            log.info("Task {} failed by spot {}, will retry", taskId, spotId);
        } else if (res == TaskFailResult.FAILED) {
            log.info("Task {} permanently failed", taskId);
        } else if (res == TaskFailResult.ALREADY_TERMINAL) {
            log.debug("Task {} already terminal (idempotent)", taskId);
        } else {
            log.warn("Failed to report failure for task {} by spot {} - result: {}", taskId, spotId, res);
        }

        return res;
    }

    /**
     * Find a task by ID.
     */
    public Optional<Task> findById(String taskId) {
        return taskRepository.findById(taskId);
    }

    /**
     * Find all tasks for a job.
     */
    public List<Task> findByJobId(String jobId) {
        return taskRepository.findByJobId(jobId);
    }

    /**
     * Get recent tasks for UI display.
     */
    public List<Task> findRecent(int limit) {
        return taskRepository.findRecent(limit);
    }

    /**
     * Find tasks by status.
     */
    public List<Task> findByStatus(TaskStatus status, int limit) {
        return taskRepository.findByStatus(status, limit);
    }

    /**
     * Create multiple tasks for a job.
     */
    public void createTasks(List<Task> tasks) {
        taskRepository.saveAll(tasks);
        log.info("Created {} tasks", tasks.size());
    }

    /**
     * Generate a new task ID.
     */
    public String generateTaskId() {
        return "task-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Count pending (NEW) tasks.
     */
    public int countPending() {
        return taskRepository.findByStatus(TaskStatus.NEW, Integer.MAX_VALUE).size();
    }

    /**
     * Count running tasks.
     */
    public int countRunning() {
        return taskRepository.findByStatus(TaskStatus.RUNNING, Integer.MAX_VALUE).size();
    }

    /**
     * Free all tasks assigned to a SPOT (when SPOT goes offline).
     */
    public int freeTasksForSpot(String spotId) {
        int freed = taskRepository.freeTasksForSpot(spotId);
        if (freed > 0) {
            log.info("Freed {} tasks from offline spot {}", freed, spotId);
        }
        return freed;
    }
}
