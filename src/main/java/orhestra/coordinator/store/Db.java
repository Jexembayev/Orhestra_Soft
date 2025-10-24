package orhestra.coordinator.store;

import java.sql.*;

public class Db implements AutoCloseable {
    public final Connection cx;

    /**
     * Пример URL:
     *  - файл:  jdbc:h2:file:./orhestra-db;AUTO_SERVER=TRUE
     *  - in-mem: jdbc:h2:mem:orhestra;DB_CLOSE_DELAY=-1
     */
    public Db(String url) {
        try {
            this.cx = DriverManager.getConnection(url);
            this.cx.setAutoCommit(false);
            ddl();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void ddl() throws SQLException {
        try (Statement st = cx.createStatement()) {

            // ---------- SPOTS (узлы/агенты) ----------
            st.addBatch("""
                create table if not exists spots(
                  spot_id      varchar primary key,
                  cpu_load     double,
                  running_tasks int,
                  status       varchar,           -- UP|DOWN
                  last_seen    timestamp,
                  total_cores  int,
                  last_ip      varchar
                );
            """);

            // ---------- TASKS ----------
            st.addBatch("""
                create table if not exists tasks(
                  id           varchar primary key,
                  payload      clob,               -- json/строка с параметрами
                  assigned_to  varchar,
                  status       varchar,            -- NEW|RUNNING|DONE|FAILED|CANCELLED
                  started_at   timestamp,
                  finished_at  timestamp,
                  alg_id       varchar,
                  run_no       int,
                  priority     int default 0,
                  attempts     int default 0,
                  created_at   timestamp default current_timestamp()
                );
            """);

            // Индексы
            st.addBatch("create index if not exists idx_tasks_status on tasks(status);");
            st.addBatch("create index if not exists idx_tasks_assigned_status on tasks(assigned_to, status);");
            st.addBatch("create index if not exists idx_tasks_prio_created on tasks(priority desc, created_at asc);");
            st.addBatch("create index if not exists idx_spots_lastseen on spots(last_seen);");

            st.executeBatch();
            cx.commit();
        }
    }

    @Override public void close() {
        try { cx.close(); } catch (Exception ignored) {}
    }
}
