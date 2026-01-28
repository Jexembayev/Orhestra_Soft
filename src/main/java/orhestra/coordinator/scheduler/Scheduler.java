package orhestra.coordinator.scheduler;

import orhestra.coordinator.config.CoordinatorConfig;
import orhestra.coordinator.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates background scheduled tasks:
 * - TaskReaper: recovers stuck RUNNING tasks
 * - SpotReaper: marks stale SPOTs as DOWN (handled by
 * SpotService.reapStaleSpots)
 * 
 * Uses a single-threaded executor to avoid concurrency issues.
 */
public class Scheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);

    private final ScheduledExecutorService executor;
    private final TaskReaper taskReaper;
    private final Runnable spotReaper;
    private final CoordinatorConfig config;

    private volatile boolean running = false;

    /**
     * Create scheduler with reapers.
     * 
     * @param taskRepository for task reaping
     * @param spotReaper     runnable to reap stale SPOTs (typically
     *                       SpotService::reapStaleSpots)
     * @param config         configuration
     */
    public Scheduler(TaskRepository taskRepository, Runnable spotReaper, CoordinatorConfig config) {
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "orhestra-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.taskReaper = new TaskReaper(taskRepository, config);
        this.spotReaper = spotReaper;
        this.config = config;
    }

    /**
     * Start the scheduler.
     */
    public void start() {
        if (running) {
            log.warn("Scheduler already running");
            return;
        }

        running = true;

        // Schedule task reaper
        long taskReaperIntervalMs = config.taskReaperInterval().toMillis();
        executor.scheduleAtFixedRate(
                taskReaper,
                taskReaperIntervalMs, // initial delay
                taskReaperIntervalMs, // interval
                TimeUnit.MILLISECONDS);
        log.info("Task reaper scheduled every {}ms", taskReaperIntervalMs);

        // Schedule spot reaper
        long spotCleanupIntervalMs = config.spotCleanupInterval().toMillis();
        executor.scheduleAtFixedRate(
                wrapRunnable("spot-reaper", spotReaper),
                spotCleanupIntervalMs,
                spotCleanupIntervalMs,
                TimeUnit.MILLISECONDS);
        log.info("Spot reaper scheduled every {}ms", spotCleanupIntervalMs);

        log.info("Scheduler started");
    }

    /**
     * Stop the scheduler gracefully.
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        executor.shutdown();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                log.warn("Scheduler forcefully stopped");
            } else {
                log.info("Scheduler stopped gracefully");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Check if scheduler is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the task reaper for direct access (e.g., manual trigger).
     */
    public TaskReaper taskReaper() {
        return taskReaper;
    }

    /**
     * Wrap a runnable with error handling.
     */
    private Runnable wrapRunnable(String name, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("{} error", name, e);
            }
        };
    }
}
