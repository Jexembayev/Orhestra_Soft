// src/main/java/orhestra/coordinator/store/dao/TaskDao.java
package orhestra.coordinator.store.dao;

import orhestra.coordinator.store.Db;
import orhestra.coordinator.core.AppBus;

// ... остальной код без изменений ...

public class TaskDao {
    private final Db db;
    public TaskDao(Db db) { this.db = db; }

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
            AppBus.fireTasksChanged(); // ← уведомляем UI
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public java.util.Optional<TaskPick> pickOneAndAssign(String spotId) {
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
            var rs = sel.executeQuery();
            if (!rs.next()) return java.util.Optional.empty();

            String id = rs.getString(1);
            String payload = rs.getString(2);

            upd.setString(1, spotId);
            upd.setTimestamp(2, java.sql.Timestamp.from(java.time.Instant.now()));
            upd.setString(3, id);
            upd.executeUpdate();

            db.cx.commit();
            AppBus.fireTasksChanged(); // ← выдача тоже меняет очередь
            return java.util.Optional.of(new TaskPick(id, payload));
        } catch (Exception e) {
            rollbackQuiet();
            throw new RuntimeException(e);
        }
    }

    public void completeOk(String id) {
        try (var ps = db.cx.prepareStatement("""
            update tasks set status='DONE', finished_at=? where id=?
        """)) {
            ps.setTimestamp(1, java.sql.Timestamp.from(java.time.Instant.now()));
            ps.setString(2, id);
            ps.executeUpdate();
            db.cx.commit();
            AppBus.fireTasksChanged(); // ← завершение
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // TaskDao.java
    public void completeOkWithMetrics(String id, long runtimeMs, Integer iter, Double fopt, String chartsJson) {
        try (var ps = db.cx.prepareStatement("""
        update tasks set status='DONE', finished_at=?,
                         runtime_ms=?, iter=?, fopt=?, charts=?
        where id=?
    """)) {
            ps.setTimestamp(1, java.sql.Timestamp.from(java.time.Instant.now()));
            if (runtimeMs <= 0) ps.setNull(2, java.sql.Types.BIGINT); else ps.setLong(2, runtimeMs);
            if (iter == null)   ps.setNull(3, java.sql.Types.INTEGER); else ps.setInt(3, iter);
            if (fopt == null)   ps.setNull(4, java.sql.Types.DOUBLE); else ps.setDouble(4, fopt);
            if (chartsJson == null) ps.setNull(5, java.sql.Types.CLOB); else ps.setString(5, chartsJson);
            ps.setString(6, id);
            ps.executeUpdate();
            db.cx.commit();
            orhestra.coordinator.core.AppBus.fireTasksChanged();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void completeFailed(String id) {
        try (var ps = db.cx.prepareStatement("""
            update tasks set status='FAILED', finished_at=? where id=?
        """)) {
            ps.setTimestamp(1, java.sql.Timestamp.from(java.time.Instant.now()));
            ps.setString(2, id);
            ps.executeUpdate();
            db.cx.commit();
            AppBus.fireTasksChanged(); // ← тоже
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int freeAllFor(String spotId) {
        try (var ps = db.cx.prepareStatement("""
            update tasks
            set status='NEW', assigned_to=null, started_at=null
            where assigned_to=? and status='RUNNING'
        """)) {
            ps.setString(1, spotId);
            int n = ps.executeUpdate();
            db.cx.commit();
            if (n > 0) AppBus.fireTasksChanged();
            return n;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // внутри TaskDao.java
    public static final class TaskView {
        public final String id;
        public final String algId;
        public final String status;
        public final Integer iter;
        public final Long runtimeMs;
        public final Double fopt;
        public final String payload;       // сырой json; можно парсить в UI при желании
        public final String assignedTo;
        public final java.sql.Timestamp startedAt;
        public final java.sql.Timestamp finishedAt;

        public TaskView(String id, String algId, String status, Integer iter, Long runtimeMs, Double fopt,
                        String payload, String assignedTo, java.sql.Timestamp startedAt, java.sql.Timestamp finishedAt) {
            this.id = id; this.algId = algId; this.status = status;
            this.iter = iter; this.runtimeMs = runtimeMs; this.fopt = fopt;
            this.payload = payload; this.assignedTo = assignedTo;
            this.startedAt = startedAt; this.finishedAt = finishedAt;
        }
    }

    /** Последние задачи для UI (DONE/RUNNING/NEW), по времени создания/старта/финиша */
    public java.util.List<TaskView> listLatest(int limit) {
        String sql = """
        select id, alg_id, status, iter, runtime_ms, fopt, payload, assigned_to, started_at, finished_at
        from tasks
        order by
          case when finished_at is not null then finished_at end desc nulls last,
          case when started_at  is not null then started_at  end desc nulls last,
          created_at desc
        limit ?
    """;
        var out = new java.util.ArrayList<TaskView>(Math.max(0, limit));
        try (var ps = db.cx.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TaskView(
                            rs.getString(1),
                            rs.getString(2),
                            rs.getString(3),
                            (Integer) (rs.getObject(4) == null ? null : rs.getInt(4)),
                            (Long)    (rs.getObject(5) == null ? null : rs.getLong(5)),
                            (Double)  (rs.getObject(6) == null ? null : rs.getDouble(6)),
                            rs.getString(7),
                            rs.getString(8),
                            rs.getTimestamp(9),
                            rs.getTimestamp(10)
                    ));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out;
    }


    private void rollbackQuiet() { try { db.cx.rollback(); } catch (Exception ignore) {} }
    public record TaskPick(String id, String payload) {}
}


