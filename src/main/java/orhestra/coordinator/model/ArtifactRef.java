package orhestra.coordinator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * S3-compatible artifact reference.
 * Points to a JAR file stored in an S3/MinIO bucket.
 */
public record ArtifactRef(
        @JsonProperty("artifactBucket") String bucket,
        @JsonProperty("artifactKey") String key,
        @JsonProperty("artifactEndpoint") String endpoint) {

    /** Derive a human-readable path for logging/display. */
    public String displayPath() {
        return bucket + "/" + key;
    }
}
