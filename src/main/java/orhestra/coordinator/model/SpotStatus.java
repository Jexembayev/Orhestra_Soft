package orhestra.coordinator.model;

/**
 * SPOT node status.
 */
public enum SpotStatus {
    /** SPOT is active and sending heartbeats */
    UP,
    /** SPOT missed heartbeats, considered offline */
    DOWN
}
