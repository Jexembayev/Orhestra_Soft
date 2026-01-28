package orhestra.coordinator.api.internal.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic response for internal API operations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationResponse(
        @JsonProperty("ok") boolean ok,
        @JsonProperty("error") String error,
        @JsonProperty("willRetry") Boolean willRetry) {
    /** Success response */
    public static OperationResponse success() {
        return new OperationResponse(true, null, null);
    }

    /** Success with retry info */
    public static OperationResponse success(boolean willRetry) {
        return new OperationResponse(true, null, willRetry);
    }

    /** Error response */
    public static OperationResponse error(String error) {
        return new OperationResponse(false, error, null);
    }

    /** Common error responses */
    public static OperationResponse taskNotFound() {
        return error("task_not_found");
    }

    public static OperationResponse alreadyCompleted() {
        return error("already_completed");
    }

    public static OperationResponse notAssigned() {
        return error("not_assigned_to_spot");
    }
}
