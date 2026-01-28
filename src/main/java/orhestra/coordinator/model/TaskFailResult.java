package orhestra.coordinator.model;

/**
 * Result of failing a task.
 */
public enum TaskFailResult {
    /** Task failed and will be retried */
    RETRIED,

    /** Task failed permanently (max attempts reached) */
    FAILED,

    /** Task was already in a terminal state - idempotent success */
    ALREADY_TERMINAL,

    /** Task not found */
    NOT_FOUND,

    /** Task is assigned to a different spot */
    WRONG_SPOT
}
