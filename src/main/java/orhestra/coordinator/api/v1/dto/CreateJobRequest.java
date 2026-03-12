package orhestra.coordinator.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating a new job.
 * POST /api/v1/jobs
 */
public record CreateJobRequest(
        @JsonProperty("artifactBucket")   String artifactBucket,
        @JsonProperty("artifactKey")      String artifactKey,
        @JsonProperty("artifactEndpoint") String artifactEndpoint,
        @JsonProperty("mainClass")        String mainClass,
        @JsonProperty("parameters")       List<ParameterGroupRequest> parameters) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * One group of parameters (maps to a schema group by groupId).
     */
    public record ParameterGroupRequest(
            @JsonProperty("groupId") String groupId,
            @JsonProperty("params")  Map<String, ParameterValue> params) {}

    /** Total number of tasks this request will generate. */
    public int totalTasks() {
        return PayloadGenerator.countTasks(parameters);
    }

    /** Serialise the parameter spec as a config JSON (stored on the job row). */
    public String config() {
        try {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("artifactBucket",   artifactBucket);
            cfg.put("artifactKey",      artifactKey);
            cfg.put("artifactEndpoint", artifactEndpoint);
            cfg.put("mainClass",        mainClass);
            cfg.put("parameters",       parameters);
            return MAPPER.writeValueAsString(cfg);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Generate one JSON payload per parameter combination. */
    public List<String> payloads() {
        return PayloadGenerator.generate(
                artifactBucket, artifactKey, artifactEndpoint, mainClass, parameters);
    }

    /** Validate that all required fields are present. */
    public void validate() {
        if (artifactBucket == null || artifactBucket.isBlank()) {
            throw new IllegalArgumentException("artifactBucket is required");
        }
        if (artifactKey == null || artifactKey.isBlank()) {
            throw new IllegalArgumentException("artifactKey is required");
        }
        if (mainClass == null || mainClass.isBlank()) {
            throw new IllegalArgumentException("mainClass is required");
        }
        if (parameters == null || parameters.isEmpty()) {
            throw new IllegalArgumentException("parameters must not be empty");
        }
        if (totalTasks() == 0) {
            throw new IllegalArgumentException("parameters expand to zero tasks — check ranges/values");
        }
    }
}
