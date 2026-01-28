package orhestra.coordinator.store;

import orhestra.coordinator.model.Job;
import orhestra.coordinator.model.JobStatus;
import orhestra.coordinator.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of JobRepository.
 */
public class JdbcJobRepository implements JobRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcJobRepository.class);

    private final Database db;

    public JdbcJobRepository(Database db) {
        this.db = db;
    }

    @Override
    public void save(Job job) {
        String sql = """
                    INSERT INTO jobs (id, jar_path, main_class, config, status, total_tasks, completed_tasks, failed_tasks, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, job.id());
            ps.setString(2, job.jarPath());
            ps.setString(3, job.mainClass());
            ps.setString(4, job.config());
            ps.setString(5, job.status().name());
            ps.setInt(6, job.totalTasks());
            ps.setInt(7, job.completedTasks());
            ps.setInt(8, job.failedTasks());
            ps.setTimestamp(9, Timestamp.from(job.createdAt() != null ? job.createdAt() : Instant.now()));

            ps.executeUpdate();
            conn.commit();

            log.debug("Saved job: {}", job.id());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save job: " + job.id(), e);
        }
    }

    @Override
    public Optional<Job> findById(String jobId) {
        String sql = "SELECT * FROM jobs WHERE id = ?";

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, jobId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find job: " + jobId, e);
        }
    }

    @Override
    public List<Job> findAll() {
        String sql = "SELECT * FROM jobs ORDER BY created_at DESC";
        return executeQuery(sql);
    }

    @Override
    public List<Job> findByStatus(JobStatus status) {
        String sql = "SELECT * FROM jobs WHERE status = ? ORDER BY created_at DESC";

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            return executeQuery(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find jobs by status", e);
        }
    }

    @Override
    public List<Job> findRecent(int limit) {
        String sql = "SELECT * FROM jobs ORDER BY created_at DESC LIMIT ?";

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limit);
            return executeQuery(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find recent jobs", e);
        }
    }

    @Override
    public boolean updateStatus(String jobId, JobStatus status) {
        String sql = "UPDATE jobs SET status = ? WHERE id = ?";

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setString(2, jobId);

            int updated = ps.executeUpdate();
            conn.commit();
            return updated > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update job status: " + jobId, e);
        }
    }

    @Override
    public int incrementCompleted(String jobId) {
        String sql = """
                    UPDATE jobs SET completed_tasks = completed_tasks + 1 WHERE id = ?
                """;

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, jobId);
            ps.executeUpdate();
            conn.commit();

            // Return new count
            return findById(jobId).map(Job::completedTasks).orElse(0);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to increment completed: " + jobId, e);
        }
    }

    @Override
    public int incrementFailed(String jobId) {
        String sql = """
                    UPDATE jobs SET failed_tasks = failed_tasks + 1 WHERE id = ?
                """;

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, jobId);
            ps.executeUpdate();
            conn.commit();

            return findById(jobId).map(Job::failedTasks).orElse(0);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to increment failed: " + jobId, e);
        }
    }

    @Override
    public void markStarted(String jobId) {
        String sql = """
                    UPDATE jobs SET status = 'RUNNING', started_at = COALESCE(started_at, ?)
                    WHERE id = ?
                """;

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, jobId);
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark job started: " + jobId, e);
        }
    }

    @Override
    public void markFinished(String jobId, JobStatus status) {
        String sql = """
                    UPDATE jobs SET status = ?, finished_at = ?
                    WHERE id = ?
                """;

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, jobId);
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark job finished: " + jobId, e);
        }
    }

    @Override
    public boolean delete(String jobId) {
        // First delete tasks, then job
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM tasks WHERE job_id = ?")) {
                ps.setString(1, jobId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM jobs WHERE id = ?")) {
                ps.setString(1, jobId);
                int deleted = ps.executeUpdate();
                conn.commit();
                return deleted > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete job: " + jobId, e);
        }
    }

    @Override
    public String generateId() {
        return "job-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // --- Helpers ---

    private List<Job> executeQuery(String sql) {
        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            return executeQuery(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute query", e);
        }
    }

    private List<Job> executeQuery(PreparedStatement ps) throws SQLException {
        List<Job> jobs = new ArrayList<>();
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            jobs.add(mapRow(rs));
        }
        return jobs;
    }

    private Job mapRow(ResultSet rs) throws SQLException {
        return Job.builder()
                .id(rs.getString("id"))
                .jarPath(rs.getString("jar_path"))
                .mainClass(rs.getString("main_class"))
                .config(rs.getString("config"))
                .status(JobStatus.valueOf(rs.getString("status")))
                .totalTasks(rs.getInt("total_tasks"))
                .completedTasks(rs.getInt("completed_tasks"))
                .failedTasks(rs.getInt("failed_tasks"))
                .createdAt(toInstant(rs.getTimestamp("created_at")))
                .startedAt(toInstant(rs.getTimestamp("started_at")))
                .finishedAt(toInstant(rs.getTimestamp("finished_at")))
                .build();
    }

    private Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
