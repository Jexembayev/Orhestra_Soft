package orhestra.coordinator.model;

/**
 * Task execution status.
 */
public enum TaskStatus {
    /** Task created, waiting to be claimed */
    NEW,
    /** Task assigned to a SPOT and being executed */
    RUNNING,
    /** Task completed successfully */
    DONE,
    /** Task failed (may be retried or permanently failed) */
    FAILED,
    /** Task cancelled by user */
    CANCELLED
}
