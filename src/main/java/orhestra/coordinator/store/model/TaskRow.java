package orhestra.coordinator.store.model;

import java.time.Instant;

public record TaskRow(
        String id,
        String payload,
        String assignedTo,
        String status,          // NEW|RUNNING|DONE|FAILED|CANCELLED
        Instant startedAt,
        Instant finishedAt,
        String algId,
        Integer runNo,
        Integer priority,
        Integer attempts,
        Instant createdAt
) {}
