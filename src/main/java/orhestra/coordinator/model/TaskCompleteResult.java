package orhestra.coordinator.model;

/**
 * Result of completing a task.
 */
public enum TaskCompleteResult {
    /** Task was successfully completed */
    COMPLETED,

    /**
     * Task was already in a terminal state (DONE or FAILED) - idempotent success
     */
    ALREADY_DONE,

    /** Task not found */
    NOT_FOUND,

    /** Task is assigned to a different spot */
    WRONG_SPOT
}
