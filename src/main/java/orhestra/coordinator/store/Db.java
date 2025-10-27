package orhestra.coordinator.store;

import java.sql.*;

public class Db implements AutoCloseable {
    public final Connection cx;

    /**
     * url:
     *  - файл:  jdbc:h2:file:./orhestra-db;AUTO_SERVER=TRUE;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE
     *  - in-mem: jdbc:h2:mem:orhestra;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE
     */
    public Db(String url) {
        try {
            // гарантируем загрузку драйвера (важно для некоторых рантаймов)
            Class.forName("org.h2.Driver");

            String effectiveUrl = (url == null || url.isBlank())
                    ? "jdbc:h2:file:./orhestra-db;AUTO_SERVER=TRUE;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE"
                    : url;

            this.cx = DriverManager.getConnection(effectiveUrl);
            this.cx.setAutoCommit(false);
            ddl();
        } catch (Exception e) {
            throw new RuntimeException("DB init failed: " + e.getMessage(), e);
        }
    }

    private void ddl() throws SQLException {
        try (Statement st = cx.createStatement()) {

            // ---------- SPOTS ----------
            st.addBatch("""
                CREATE TABLE IF NOT EXISTS spots(
                  spot_id        VARCHAR PRIMARY KEY,
                  cpu_load       DOUBLE,
                  running_tasks  INT,
                  status         VARCHAR,           -- UP|DOWN
                  last_seen      TIMESTAMP,
                  total_cores    INT,
                  last_ip        VARCHAR
                );
            """);

            // ---------- TASKS ----------
            st.addBatch("""
                CREATE TABLE IF NOT EXISTS tasks(
                  id            VARCHAR PRIMARY KEY,
                  payload       CLOB            NOT NULL, -- json с параметрами
                  assigned_to   VARCHAR,
                  status        VARCHAR         NOT NULL, -- NEW|RUNNING|DONE|FAILED|CANCELLED
                  started_at    TIMESTAMP,
                  finished_at   TIMESTAMP,
                  alg_id        VARCHAR,
                  run_no        INT,
                  priority      INT DEFAULT 0,
                  attempts      INT DEFAULT 0,
                  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // Расширения под метрики результатов (idempotent)
            st.addBatch("ALTER TABLE tasks ADD COLUMN IF NOT EXISTS runtime_ms BIGINT;");
            st.addBatch("ALTER TABLE tasks ADD COLUMN IF NOT EXISTS iter       INT;");
            st.addBatch("ALTER TABLE tasks ADD COLUMN IF NOT EXISTS fopt       DOUBLE;");
            st.addBatch("ALTER TABLE tasks ADD COLUMN IF NOT EXISTS charts     CLOB;");

            // Индексы
            st.addBatch("CREATE INDEX IF NOT EXISTS idx_tasks_status            ON tasks(status);");
            st.addBatch("CREATE INDEX IF NOT EXISTS idx_tasks_assigned_status  ON tasks(assigned_to, status);");
            st.addBatch("CREATE INDEX IF NOT EXISTS idx_tasks_prio_created     ON tasks(priority, created_at);");
            st.addBatch("CREATE INDEX IF NOT EXISTS idx_spots_lastseen         ON spots(last_seen);");

            st.executeBatch();
            cx.commit();
        }
    }

    @Override public void close() {
        try { cx.close(); } catch (Exception ignored) {}
    }
}


