package orhestra.coordinator.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import orhestra.coordinator.model.Task;

import java.time.Instant;

/**
 * Response DTO for task result.
 * Used in JobResponse.results array.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskResultResponse(
        @JsonProperty("taskId") String taskId,
        @JsonProperty("status") String status,
        @JsonProperty("algorithm") String algorithm,
        @JsonProperty("iterations") Integer iterations,
        @JsonProperty("agents") Integer agents,
        @JsonProperty("dimension") Integer dimension,
        @JsonProperty("runtimeMs") Long runtimeMs,
        @JsonProperty("iter") Integer iter,
        @JsonProperty("fopt") Double fopt,
        @JsonProperty("assignedTo") String assignedTo,
        @JsonProperty("startedAt") Instant startedAt,
        @JsonProperty("finishedAt") Instant finishedAt,
        @JsonProperty("errorMessage") String errorMessage) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Create response from domain model.
     * Parses task.payload() JSON to extract algorithm parameters.
     * Payload format:
     * {"alg":"PSO","iterations":{"max":100},"agents":10,"dimension":2}
     */
    public static TaskResultResponse from(Task task) {
        String algorithm = null;
        Integer iterations = null;
        Integer agents = null;
        Integer dimension = null;

        try {
            if (task.payload() != null && !task.payload().isBlank()) {
                JsonNode root = MAPPER.readTree(task.payload());

                // "alg" field → algorithm name
                if (root.has("alg") && !root.get("alg").isNull()) {
                    algorithm = root.get("alg").asText();
                }

                // "iterations" → either {"max": N} object or plain number
                JsonNode iterNode = root.path("iterations");
                if (iterNode.isObject() && iterNode.has("max")) {
                    iterations = iterNode.get("max").asInt();
                } else if (iterNode.isNumber()) {
                    iterations = iterNode.asInt();
                }

                // "agents" → plain number
                if (root.has("agents") && root.get("agents").isNumber()) {
                    agents = root.get("agents").asInt();
                }

                // "dimension" → plain number
                if (root.has("dimension") && root.get("dimension").isNumber()) {
                    dimension = root.get("dimension").asInt();
                }
            }
        } catch (Exception ignored) {
            // Malformed payload — leave fields as null
        }

        return new TaskResultResponse(
                task.id(),
                task.status().name(),
                algorithm,
                iterations,
                agents,
                dimension,
                task.runtimeMs(),
                task.iter(),
                task.fopt(),
                task.assignedTo(),
                task.startedAt(),
                task.finishedAt(),
                task.errorMessage());
    }
}
