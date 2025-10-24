package orhestra.coordinator.store.model;

import java.time.Instant;

public record SpotNode(
        String spotId,
        Double cpuLoad,
        Integer runningTasks,
        String status,      // "UP"/"DOWN"
        Instant lastSeen,
        Integer totalCores,
        String lastIp
) {}
