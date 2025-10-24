package orhestra.cloud.model;

public record CloudInstance(
        String id,
        String name,
        String zone,
        String status,
        String ip,
        boolean preemptible
) {}

