package orhestra.coordinator.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * A single parameter value specification inside a job request.
 *
 * <p>Supported types (discriminated by {@code type} field):
 * <ul>
 *   <li>{@code CONSTANT}   – {@code value} field holds the concrete value.</li>
 *   <li>{@code INT_RANGE}  – {@code min}, {@code max}, {@code step} expand to a list of ints.</li>
 *   <li>{@code FLOAT_RANGE}– {@code min}, {@code max}, {@code step} expand to a list of doubles.</li>
 *   <li>{@code ENUM_LIST}  – {@code values} holds the list of string options.</li>
 * </ul>
 */
public record ParameterValue(
        @JsonProperty("type") String type,
        @JsonProperty("min") Number min,
        @JsonProperty("max") Number max,
        @JsonProperty("step") Number step,
        @JsonProperty("value") Object value,
        @JsonProperty("values") List<String> values) {

    /**
     * Expand this parameter spec into a concrete list of values for the Cartesian product.
     */
    public List<Object> expand() {
        return switch (type == null ? "" : type.toUpperCase()) {
            case "CONSTANT" -> List.of(value);
            case "INT_RANGE" -> {
                int lo = min.intValue();
                int hi = max.intValue();
                int s  = step != null ? step.intValue() : 1;
                if (s <= 0 || hi < lo) yield List.of();
                List<Object> vals = new ArrayList<>();
                for (int v = lo; v <= hi; v += s) vals.add(v);
                yield vals;
            }
            case "FLOAT_RANGE" -> {
                double lo = min.doubleValue();
                double hi = max.doubleValue();
                double s  = step != null ? step.doubleValue() : 1.0;
                if (s <= 0 || hi < lo) yield List.of();
                List<Object> vals = new ArrayList<>();
                for (double v = lo; v <= hi + 1e-12; v += s) vals.add(v);
                yield vals;
            }
            case "ENUM_LIST" -> values != null ? new ArrayList<>(values) : List.of();
            default -> List.of();
        };
    }
}
