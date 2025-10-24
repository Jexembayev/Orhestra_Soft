package orhestra.coordinator.store.dao;

import orhestra.coordinator.store.Db;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class TaskDao {
    private final Db db;

    public TaskDao(Db db) { this.db = db; }

    /** положить новую задачу в очередь */
    public void enqueue(String id, String payload, String algId, int runNo, int priority) {
        String sql = """
            insert into tasks(id, payload, status, alg_id, run_no, priority, attempts)
            values(?, ?, 'NEW', ?, ?, ?, 0)
        """;
        try (var ps = db.cx.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, payload);
            ps.setString(3, algId);
            ps.setInt(4, runNo);
            ps.setInt(5, priority);
            ps.executeUpdate();
            db.cx.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Выдать одну свободную задачу с блокировкой строки.
     * В H2 используем «select … for update» и SKIP LOCKED аналог: в H2 его нет, но
     * одновременных координаторов у нас нет — одного достаточно.
     */
    public Optional<TaskPick> pickOneAndAssign(String spotId) {
        try (var sel = db.cx.prepareStatement("""
                select id, payload
                from tasks
                where status='NEW'
                order by priority desc, created_at asc
                limit 1
                for update
            """);
             var upd = db.cx.prepareStatement("""
                update tasks
                set status='RUNNING', assigned_to=?, started_at=?, attempts=attempts+1
                where id=?
            """)) {

            ResultSet rs = sel.executeQuery();
            if (!rs.next()) return Optional.empty();

            String id = rs.getString(1);
            String payload = rs.getString(2);

            upd.setString(1, spotId);
            upd.setTimestamp(2, Timestamp.from(Instant.now()));
            upd.setString(3, id);
            upd.executeUpdate();

            db.cx.commit();
            return Optional.of(new TaskPick(id, payload));
        } catch (Exception e) {
            rollbackQuiet();
            throw new RuntimeException(e);
        }
    }

    public void completeOk(String id) {
        try (var ps = db.cx.prepareStatement("""
            update tasks set status='DONE', finished_at=? where id=?
        """)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, id);
            ps.executeUpdate();
            db.cx.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void completeFailed(String id) {
        try (var ps = db.cx.prepareStatement("""
            update tasks set status='FAILED', finished_at=? where id=?
        """)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, id);
            ps.executeUpdate();
            db.cx.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** освободить все задачи, «залипшие» за данным агентом */
    public int freeAllFor(String spotId) {
        try (var ps = db.cx.prepareStatement("""
            update tasks
            set status='NEW', assigned_to=null, started_at=null
            where assigned_to=? and status='RUNNING'
        """)) {
            ps.setString(1, spotId);
            int n = ps.executeUpdate();
            db.cx.commit();
            return n;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void rollbackQuiet() { try { db.cx.rollback(); } catch (Exception ignore) {} }

    /** маленький DTO для «выдачи» задания агенту */
    public record TaskPick(String id, String payload) {}
}

