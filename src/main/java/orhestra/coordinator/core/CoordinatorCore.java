package orhestra.coordinator.core;

import orhestra.coordinator.store.Db;
import orhestra.coordinator.store.dao.SpotDao;
import orhestra.coordinator.store.dao.TaskDao;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class CoordinatorCore {
    private final SpotDao spots;
    private final TaskDao tasks;

    public CoordinatorCore(Db db) {
        this.spots = new SpotDao(db);
        this.tasks = new TaskDao(db);
    }

    // ---- SPOTS ----
    public void heartbeat(String spotId, double cpu, int running, int cores, String ip) {
        spots.upsertHeartbeat(spotId, cpu, running, cores, ip);
    }

    /** пометить отвалившихся и освободить их задачи */
    public int sweepOffline(Duration offline, java.util.function.Consumer<String> log) {
        Instant cutoff = Instant.now().minus(offline);
        int down = spots.markDownOlderThan(cutoff);
        // Реальное освобождение задач сделаем на уровне TaskDao из таймера,
        // если надо — можно расширить, но часто достаточно освобождать
        // при следующем тикe или по reconnect.
        if (down > 0 && log != null) log.accept("Marked DOWN: " + down);
        return down;
    }

    // ---- TASKS ----
    public Optional<TaskDao.TaskPick> getTaskFor(String spotId) {
        return tasks.pickOneAndAssign(spotId);
    }

    public void taskDone(String taskId) {
        tasks.completeOk(taskId);
    }

    public void taskFailed(String taskId) {
        tasks.completeFailed(taskId);
    }

    public int freeTasksOf(String spotId) {
        return tasks.freeAllFor(spotId);
    }
}

