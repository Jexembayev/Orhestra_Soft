package orhestra.coordinator.scheduler;

import orhestra.coordinator.config.CoordinatorConfig;
import orhestra.coordinator.core.AppBus;
import orhestra.coordinator.model.Task;
import orhestra.coordinator.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Background task that recovers stuck RUNNING tasks.
 * 
 * Tasks can get stuck if:
 * - A SPOT crashes while processing
 * - Network issues prevent completion notification
 * - A SPOT is preempted (cloud spot instances)
 * 
 * The reaper:
 * 1. Finds tasks that have been RUNNING longer than the threshold
 * 2. For each stuck task:
 * - If attempts < maxAttempts: reset to NEW for retry
 * - Otherwise: mark as FAILED
 */
public class TaskReaper implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TaskReaper.class);

    private final TaskRepository taskRepository;
    private final CoordinatorConfig config;

    public TaskReaper(TaskRepository taskRepository, CoordinatorConfig config) {
        this.taskRepository = taskRepository;
        this.config = config;
    }

    @Override
    public void run() {
        try {
            int reaped = reapStuckTasks();
            if (reaped > 0) {
                AppBus.fireTasksChanged();
            }
        } catch (Exception e) {
            log.error("Task reaper error", e);
        }
    }

    /**
     * Find and recover stuck RUNNING tasks.
     * 
     * @return number of tasks recovered
     */
    public int reapStuckTasks() {
        Instant cutoff = Instant.now().minus(config.taskStuckThreshold());

        List<Task> stuck = taskRepository.findStuckRunning(cutoff);

        if (stuck.isEmpty()) {
            log.debug("No stuck tasks found");
            return 0;
        }

        int retried = 0;
        int failed = 0;

        for (Task task : stuck) {
            try {
                if (task.canRetry()) {
                    // Reset to NEW for retry
                    taskRepository.resetToNew(task.id());
                    retried++;
                    log.info("Reaped task {} for retry (attempt {} of {})",
                            task.id(), task.attempts(), task.maxAttempts());
                } else {
                    // Exhausted retries - mark as permanently failed
                    taskRepository.markFailed(task.id(),
                            "Task stuck in RUNNING - max attempts exceeded (" + task.attempts() + "/"
                                    + task.maxAttempts() + ")");
                    failed++;
                    log.warn("Task {} permanently failed after {} attempts (stuck in RUNNING)",
                            task.id(), task.attempts());
                }
            } catch (Exception e) {
                log.error("Failed to reap task {}", task.id(), e);
            }
        }

        log.info("Task reaper: {} retried, {} failed, {} total stuck",
                retried, failed, stuck.size());

        return retried + failed;
    }
}
