package orhestra.coordinator.config;

import java.time.Duration;

/**
 * Configuration holder for Coordinator settings.
 * All settings have sensible defaults.
 */
public final class CoordinatorConfig {

    // Database settings
    private String databaseUrl = "jdbc:h2:file:./data/orhestra;AUTO_SERVER=TRUE;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE";
    private int databasePoolSize = 10;

    // Server settings
    private int serverPort = 8080;
    private String serverHost = "0.0.0.0";

    // Task settings
    private int defaultMaxAttempts = 3;
    private Duration taskStuckThreshold = Duration.ofMinutes(5);
    private Duration taskReaperInterval = Duration.ofSeconds(30);

    // SPOT settings
    private Duration spotHeartbeatTimeout = Duration.ofSeconds(10);
    private Duration spotCleanupInterval = Duration.ofSeconds(5);

    // Auth settings (optional)
    private String agentKey = null; // If set, SPOTs must provide X-Orhestra-Key header

    private CoordinatorConfig() {
    }

    public static CoordinatorConfig defaults() {
        return new CoordinatorConfig();
    }

    public static CoordinatorConfig fromEnv() {
        CoordinatorConfig config = new CoordinatorConfig();

        // Override from environment variables
        String dbUrl = System.getenv("ORHESTRA_DB_URL");
        if (dbUrl != null && !dbUrl.isBlank()) {
            config.databaseUrl = dbUrl;
        }

        String port = System.getenv("ORHESTRA_PORT");
        if (port != null && !port.isBlank()) {
            config.serverPort = Integer.parseInt(port);
        }

        String agentKey = System.getenv("ORHESTRA_AGENT_KEY");
        if (agentKey != null && !agentKey.isBlank()) {
            config.agentKey = agentKey;
        }

        String maxAttempts = System.getenv("ORHESTRA_MAX_ATTEMPTS");
        if (maxAttempts != null && !maxAttempts.isBlank()) {
            config.defaultMaxAttempts = Integer.parseInt(maxAttempts);
        }

        return config;
    }

    // Getters
    public String databaseUrl() {
        return databaseUrl;
    }

    public int databasePoolSize() {
        return databasePoolSize;
    }

    public int serverPort() {
        return serverPort;
    }

    public String serverHost() {
        return serverHost;
    }

    public int defaultMaxAttempts() {
        return defaultMaxAttempts;
    }

    public Duration taskStuckThreshold() {
        return taskStuckThreshold;
    }

    public Duration taskReaperInterval() {
        return taskReaperInterval;
    }

    public Duration spotHeartbeatTimeout() {
        return spotHeartbeatTimeout;
    }

    public Duration spotCleanupInterval() {
        return spotCleanupInterval;
    }

    public String agentKey() {
        return agentKey;
    }

    public boolean hasAgentKey() {
        return agentKey != null && !agentKey.isBlank();
    }

    // Fluent setters for testing/customization
    public CoordinatorConfig withDatabaseUrl(String url) {
        this.databaseUrl = url;
        return this;
    }

    public CoordinatorConfig withServerPort(int port) {
        this.serverPort = port;
        return this;
    }

    public CoordinatorConfig withAgentKey(String key) {
        this.agentKey = key;
        return this;
    }

    public CoordinatorConfig withMaxAttempts(int attempts) {
        this.defaultMaxAttempts = attempts;
        return this;
    }

    public CoordinatorConfig withTaskStuckThreshold(Duration threshold) {
        this.taskStuckThreshold = threshold;
        return this;
    }

    @Override
    public String toString() {
        return "CoordinatorConfig{" +
                "databaseUrl='" + databaseUrl + '\'' +
                ", serverPort=" + serverPort +
                ", maxAttempts=" + defaultMaxAttempts +
                ", agentKeySet=" + hasAgentKey() +
                '}';
    }
}
