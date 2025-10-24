package orhestra.coordinator.store.dao;

import orhestra.coordinator.store.Db;

import java.sql.Timestamp;
import java.time.Instant;

public class SpotDao {
    private final Db db;

    public SpotDao(Db db) { this.db = db; }

    /** upsert heartbeat + базовая информация */
    public void upsertHeartbeat(String spotId, double cpuLoad, int runningTasks,
                                int totalCores, String lastIp) {
        String sql = """
            merge into spots(spot_id, cpu_load, running_tasks, status, last_seen, total_cores, last_ip)
            key(spot_id)
            values(?, ?, ?, 'UP', ?, ?, ?)
        """;
        try (var ps = db.cx.prepareStatement(sql)) {
            ps.setString(1, spotId);
            ps.setDouble(2, cpuLoad);
            ps.setInt(3, runningTasks);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.setInt(5, totalCores);
            ps.setString(6, lastIp);
            ps.executeUpdate();
            db.cx.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** пометить DOWN и освободить задачи — обычно делает координационный таймер */
    public int markDownOlderThan(Instant cutoff) {
        String sql = "update spots set status='DOWN' where last_seen < ? and status <> 'DOWN'";
        try (var ps = db.cx.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(cutoff));
            int n = ps.executeUpdate();
            db.cx.commit();
            return n;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** удалить запись агента (редко нужно) */
    public int delete(String spotId) {
        try (var ps = db.cx.prepareStatement("delete from spots where spot_id=?")) {
            ps.setString(1, spotId);
            int n = ps.executeUpdate();
            db.cx.commit();
            return n;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

