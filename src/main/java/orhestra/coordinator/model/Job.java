package orhestra.coordinator.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain model representing a computation job.
 * A job consists of multiple tasks generated from parameter combinations.
 */
public final class Job {
    private final String id;
    private final String jarPath;
    private final String mainClass;
    private final String config; // JSON with iterations/agents/dimension ranges
    private final JobStatus status;
    private final int totalTasks;
    private final int completedTasks;
    private final int failedTasks;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant finishedAt;

    private Job(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id is required");
        this.jarPath = Objects.requireNonNull(builder.jarPath, "jarPath is required");
        this.mainClass = Objects.requireNonNull(builder.mainClass, "mainClass is required");
        this.config = Objects.requireNonNull(builder.config, "config is required");
        this.status = Objects.requireNonNull(builder.status, "status is required");
        this.totalTasks = builder.totalTasks;
        this.completedTasks = builder.completedTasks;
        this.failedTasks = builder.failedTasks;
        this.createdAt = builder.createdAt;
        this.startedAt = builder.startedAt;
        this.finishedAt = builder.finishedAt;
    }

    // Getters
    public String id() {
        return id;
    }

    public String jarPath() {
        return jarPath;
    }

    public String mainClass() {
        return mainClass;
    }

    public String config() {
        return config;
    }

    public JobStatus status() {
        return status;
    }

    public int totalTasks() {
        return totalTasks;
    }

    public int completedTasks() {
        return completedTasks;
    }

    public int failedTasks() {
        return failedTasks;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    /** Calculate progress percentage */
    public int progressPercent() {
        if (totalTasks == 0)
            return 0;
        return (completedTasks + failedTasks) * 100 / totalTasks;
    }

    /** Check if job is in terminal state */
    public boolean isTerminal() {
        return status == JobStatus.COMPLETED ||
                status == JobStatus.FAILED ||
                status == JobStatus.CANCELLED;
    }

    /** Create a builder from this job (for updates) */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .jarPath(jarPath)
                .mainClass(mainClass)
                .config(config)
                .status(status)
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .failedTasks(failedTasks)
                .createdAt(createdAt)
                .startedAt(startedAt)
                .finishedAt(finishedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String jarPath;
        private String mainClass;
        private String config;
        private JobStatus status = JobStatus.PENDING;
        private int totalTasks;
        private int completedTasks;
        private int failedTasks;
        private Instant createdAt;
        private Instant startedAt;
        private Instant finishedAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder jarPath(String jarPath) {
            this.jarPath = jarPath;
            return this;
        }

        public Builder mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        public Builder config(String config) {
            this.config = config;
            return this;
        }

        public Builder status(JobStatus status) {
            this.status = status;
            return this;
        }

        public Builder totalTasks(int totalTasks) {
            this.totalTasks = totalTasks;
            return this;
        }

        public Builder completedTasks(int completedTasks) {
            this.completedTasks = completedTasks;
            return this;
        }

        public Builder failedTasks(int failedTasks) {
            this.failedTasks = failedTasks;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder finishedAt(Instant finishedAt) {
            this.finishedAt = finishedAt;
            return this;
        }

        public Job build() {
            return new Job(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Job job))
            return false;
        return Objects.equals(id, job.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Job{id='" + id + "', status=" + status + ", progress=" + progressPercent() + "%}";
    }
}
