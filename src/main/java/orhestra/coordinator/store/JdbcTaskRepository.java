package orhestra.coordinator.store;

import orhestra.coordinator.model.Task;
import orhestra.coordinator.model.TaskCompleteResult;
import orhestra.coordinator.model.TaskFailResult;
import orhestra.coordinator.model.TaskStatus;
import orhestra.coordinator.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of TaskRepository.
 * Uses pessimistic locking for atomic task claiming.
 */
public class JdbcTaskRepository implements TaskRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcTaskRepository.class);

    private final Database db;

    public JdbcTaskRepository(Database db) {
        this.db = db;
    }

    @Override
    public void save(Task task) {
        String sql = """
                    INSERT INTO tasks (id, job_id, payload, status, assigned_to, priority, attempts, max_attempts,
                                       error_message, created_at, started_at, finished_at, runtime_ms, iter, fopt, result)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, task.id());
            ps.setString(2, task.jobId());
            ps.setString(3, task.payload());
            ps.setString(4, task.status().name());
            ps.setString(5, task.assignedTo());
            ps.setInt(6, task.priority());
            ps.setInt(7, task.attempts());
            ps.setInt(8, task.maxAttempts());
            ps.setString(9, task.errorMessage());
            setTimestamp(ps, 10, task.createdAt());
            setTimestamp(ps, 11, task.startedAt());
            setTimestamp(ps, 12, task.finishedAt());
            setLongOrNull(ps, 13, task.runtimeMs());
            setIntOrNull(ps, 14, task.iter());
            setDoubleOrNull(ps, 15, task.fopt());
            ps.setString(16, task.result());

            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save task: " + task.id(), e);
        }
    }

    @Override
    public void saveAll(List<Task> tasks) {
        if (tasks.isEmpty())
            return;

        String sql = """
                    INSERT INTO tasks (id, job_id, payload, status, priority, attempts, max_attempts, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            for (Task task : tasks) {
                ps.setString(1, task.id());
                ps.setString(2, task.jobId());
                ps.setString(3, task.payload());
                ps.setString(4, task.status().name());
                ps.setInt(5, task.priority());
                ps.setInt(6, task.attempts());
                ps.setInt(7, task.maxAttempts());
                setTimestamp(ps, 8, task.createdAt() != null ? task.createdAt() : Instant.now());
                ps.addBatch();
            }

            ps.executeBatch();
            conn.commit();

            log.debug("Saved {} tasks in batch", tasks.size());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save tasks batch", e);
        }
    }

    @Override
    public Optional<Task> findById(String taskId) {
        String sql = "SELECT * FROM tasks WHERE id = ?";

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find task: " + taskId, e);
        }
    }

    @Override
    public List<Task> findByJobId(String jobId) {
        String sql = "SELECT * FROM tasks WHERE job_id = ? ORDER BY created_at";

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, jobId);
            return executeQuery(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find tasks for job: " + jobId, e);
        }
    }

    @Override
    public List<Task> findByStatus(TaskStatus status, int limit) {
        String sql = "SELECT * FROM tasks WHERE status = ? ORDER BY priority DESC, created_at LIMIT ?";

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setInt(2, limit);
            return executeQuery(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find tasks by status: " + status, e);
        }
    }

    @Override
    public List<Task> claimTasks(String spotId, int maxTasks) {
        // Use SELECT FOR UPDATE to lock rows, then update them
        String selectSql = """
                    SELECT id, payload FROM tasks
                    WHERE status = 'NEW'
                    ORDER BY priority DESC, created_at
                    LIMIT ?
                    FOR UPDATE
                """;

        String updateSql = """
                    UPDATE tasks
                    SET status = 'RUNNING', assigned_to = ?, started_at = ?, attempts = attempts + 1
                    WHERE id = ?
                """;

        List<Task> claimed = new ArrayList<>();

        try (Connection conn = db.getConnection()) {
            try (PreparedStatement selectPs = conn.prepareStatement(selectSql);
                    PreparedStatement updatePs = conn.prepareStatement(updateSql)) {

                selectPs.setInt(1, maxTasks);

                try (ResultSet rs = selectPs.executeQuery()) {
                    Timestamp now = Timestamp.from(Instant.now());

                    while (rs.next()) {
                        String id = rs.getString("id");
                        String payload = rs.getString("payload");

                        updatePs.setString(1, spotId);
                        updatePs.setTimestamp(2, now);
                        updatePs.setString(3, id);
                        updatePs.addBatch();

                        // Build claimed task (minimal info needed by SPOT)
                        claimed.add(Task.builder()
                                .id(id)
                                .payload(payload)
                                .status(TaskStatus.RUNNING)
                                .assignedTo(spotId)
                                .startedAt(now.toInstant())
                                .build());
                    }
                }

                if (!claimed.isEmpty()) {
                    updatePs.executeBatch();
                }

                conn.commit();

                if (!claimed.isEmpty()) {
                    log.debug("Claimed {} tasks for spot {}", claimed.size(), spotId);
                }

                return claimed;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to claim tasks for spot: " + spotId, e);
        }
    }

    @Override
    public boolean complete(String taskId, String spotId, long runtimeMs, Integer iter, Double fopt, String result) {
        String sql = """
                    UPDATE tasks
                    SET status = 'DONE', finished_at = ?, runtime_ms = ?, iter = ?, fopt = ?, result = ?
                    WHERE id = ? AND assigned_to = ? AND status = 'RUNNING'
                """;

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setLong(2, runtimeMs);
            setIntOrNull(ps, 3, iter);
            setDoubleOrNull(ps, 4, fopt);
            ps.setString(5, result);
            ps.setString(6, taskId);
            ps.setString(7, spotId);

            int updated = ps.executeUpdate();
            conn.commit();

            if (updated > 0) {
                log.debug("Task {} completed by spot {}", taskId, spotId);
            }

            return updated > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to complete task: " + taskId, e);
        }
    }

    @Override
    public boolean fail(String taskId, String spotId, String errorMessage, boolean retriable) {
        // First, get the task to check attempts
        Optional<Task> taskOpt = findById(taskId);
        if (taskOpt.isEmpty()) {
            return false;
        }

        Task task = taskOpt.get();

        // Verify the spot owns this task
        if (!spotId.equals(task.assignedTo())) {
            log.warn("Spot {} tried to fail task {} but it's assigned to {}", spotId, taskId, task.assignedTo());
            return false;
        }

        if (task.status() != TaskStatus.RUNNING) {
            log.warn("Task {} is not RUNNING, status: {}", taskId, task.status());
            return false;
        }

        boolean willRetry = retriable && task.canRetry();

        if (willRetry) {
            // Reset to NEW for retry
            resetToNew(taskId);
            return true; // willRetry = true
        } else {
            // Mark as permanently failed
            markFailed(taskId, errorMessage);
            return false; // willRetry = false
        }
    }

    @Override
    public List<Task> findStuckRunning(Instant startedBefore) {
        String sql = """
                    SELECT * FROM tasks
                    WHERE status = 'RUNNING' AND started_at < ?
                    ORDER BY started_at
                """;

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.from(startedBefore));
            return executeQuery(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find stuck tasks", e);
        }
    }

    @Override
    public boolean resetToNew(String taskId) {
        String sql = """
                    UPDATE tasks
                    SET status = 'NEW', assigned_to = NULL, started_at = NULL, error_message = NULL
                    WHERE id = ?
                """;

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, taskId);
            int updated = ps.executeUpdate();
            conn.commit();

            if (updated > 0) {
                log.debug("Task {} reset to NEW for retry", taskId);
            }

            return updated > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to reset task: " + taskId, e);
        }
    }

    @Override
    public boolean markFailed(String taskId, String errorMessage) {
        String sql = """
                    UPDATE tasks
                    SET status = 'FAILED', finished_at = ?, error_message = ?
                    WHERE id = ?
                """;

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, errorMessage);
            ps.setString(3, taskId);

            int updated = ps.executeUpdate();
            conn.commit();

            if (updated > 0) {
                log.debug("Task {} marked as FAILED: {}", taskId, errorMessage);
            }

            return updated > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark task as failed: " + taskId, e);
        }
    }

    @Override
    public int freeTasksForSpot(String spotId) {
        String sql = """
                    UPDATE tasks
                    SET status = 'NEW', assigned_to = NULL, started_at = NULL
                    WHERE assigned_to = ? AND status = 'RUNNING'
                """;

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, spotId);
            int freed = ps.executeUpdate();
            conn.commit();

            if (freed > 0) {
                log.info("Freed {} tasks from offline spot {}", freed, spotId);
            }

            return freed;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to free tasks for spot: " + spotId, e);
        }
    }

    @Override
    public int countByJobIdAndStatus(String jobId, TaskStatus status) {
        String sql = "SELECT COUNT(*) FROM tasks WHERE job_id = ? AND status = ?";

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, jobId);
            ps.setString(2, status.name());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count tasks", e);
        }
    }

    @Override
    public List<Task> findRecent(int limit) {
        String sql = """
                    SELECT * FROM tasks
                    ORDER BY
                        COALESCE(finished_at, started_at, created_at) DESC
                    LIMIT ?
                """;

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limit);
            return executeQuery(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find recent tasks", e);
        }
    }

    @Override
    public TaskCompleteResult completeIdempotent(String taskId, String spotId, long runtimeMs, Integer iter,
            Double fopt, String result) {
        Optional<Task> taskOpt = findById(taskId);
        if (taskOpt.isEmpty()) {
            return TaskCompleteResult.NOT_FOUND;
        }

        Task task = taskOpt.get();

        // Check if already completed (idempotent)
        if (task.status() == TaskStatus.DONE) {
            return TaskCompleteResult.ALREADY_DONE;
        }

        // Also treat FAILED as already terminal
        if (task.status() == TaskStatus.FAILED) {
            return TaskCompleteResult.ALREADY_DONE;
        }

        // Verify the spot owns this task
        if (task.assignedTo() != null && !spotId.equals(task.assignedTo())) {
            log.warn("Spot {} tried to complete task {} but it's assigned to {}", spotId, taskId, task.assignedTo());
            return TaskCompleteResult.WRONG_SPOT;
        }

        // Task must be RUNNING
        if (task.status() != TaskStatus.RUNNING) {
            log.warn("Task {} is not RUNNING (status: {}), treating as idempotent success", taskId, task.status());
            return TaskCompleteResult.ALREADY_DONE;
        }

        // Perform the completion
        boolean updated = complete(taskId, spotId, runtimeMs, iter, fopt, result);
        return updated ? TaskCompleteResult.COMPLETED : TaskCompleteResult.ALREADY_DONE;
    }

    @Override
    public TaskFailResult failIdempotent(String taskId, String spotId, String errorMessage, boolean retriable) {
        Optional<Task> taskOpt = findById(taskId);
        if (taskOpt.isEmpty()) {
            return TaskFailResult.NOT_FOUND;
        }

        Task task = taskOpt.get();

        // Check if already terminal (idempotent)
        if (task.status() == TaskStatus.DONE || task.status() == TaskStatus.FAILED
                || task.status() == TaskStatus.CANCELLED) {
            return TaskFailResult.ALREADY_TERMINAL;
        }

        // Verify the spot owns this task
        if (task.assignedTo() != null && !spotId.equals(task.assignedTo())) {
            log.warn("Spot {} tried to fail task {} but it's assigned to {}", spotId, taskId, task.assignedTo());
            return TaskFailResult.WRONG_SPOT;
        }

        // Task must be RUNNING
        if (task.status() != TaskStatus.RUNNING) {
            log.warn("Task {} is not RUNNING (status: {}), treating as already terminal", taskId, task.status());
            return TaskFailResult.ALREADY_TERMINAL;
        }

        boolean willRetry = retriable && task.canRetry();

        if (willRetry) {
            resetToNew(taskId);
            return TaskFailResult.RETRIED;
        } else {
            markFailed(taskId, errorMessage);
            return TaskFailResult.FAILED;
        }
    }

    @Override
    public boolean updateStatus(String taskId, TaskStatus status) {
        String sql = "UPDATE tasks SET status = ? WHERE id = ?";

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setString(2, taskId);

            int updated = ps.executeUpdate();
            conn.commit();
            return updated > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update task status: " + taskId, e);
        }
    }

    // Helper methods

    private List<Task> executeQuery(PreparedStatement ps) throws SQLException {
        List<Task> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private Task mapRow(ResultSet rs) throws SQLException {
        return Task.builder()
                .id(rs.getString("id"))
                .jobId(rs.getString("job_id"))
                .payload(rs.getString("payload"))
                .status(TaskStatus.valueOf(rs.getString("status")))
                .assignedTo(rs.getString("assigned_to"))
                .priority(rs.getInt("priority"))
                .attempts(rs.getInt("attempts"))
                .maxAttempts(rs.getInt("max_attempts"))
                .errorMessage(rs.getString("error_message"))
                .createdAt(toInstant(rs.getTimestamp("created_at")))
                .startedAt(toInstant(rs.getTimestamp("started_at")))
                .finishedAt(toInstant(rs.getTimestamp("finished_at")))
                .runtimeMs(getLongOrNull(rs, "runtime_ms"))
                .iter(getIntOrNull(rs, "iter"))
                .fopt(getDoubleOrNull(rs, "fopt"))
                .result(rs.getString("result"))
                .build();
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private static void setTimestamp(PreparedStatement ps, int index, Instant instant) throws SQLException {
        if (instant != null) {
            ps.setTimestamp(index, Timestamp.from(instant));
        } else {
            ps.setNull(index, Types.TIMESTAMP);
        }
    }

    private static void setLongOrNull(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, Types.BIGINT);
        }
    }

    private static void setIntOrNull(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    private static void setDoubleOrNull(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value != null) {
            ps.setDouble(index, value);
        } else {
            ps.setNull(index, Types.DOUBLE);
        }
    }

    private static Long getLongOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer getIntOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Double getDoubleOrNull(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
