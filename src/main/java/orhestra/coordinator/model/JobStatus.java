package orhestra.coordinator.model;

/**
 * Job status representing the overall state of a computation job.
 */
public enum JobStatus {
    /** Job created, no tasks started yet */
    PENDING,
    /** At least one task is running */
    RUNNING,
    /** All tasks completed successfully */
    COMPLETED,
    /** Job failed (some tasks failed beyond retry limit) */
    FAILED,
    /** Job cancelled by user */
    CANCELLED
}
