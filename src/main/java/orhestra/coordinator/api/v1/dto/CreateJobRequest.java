package orhestra.coordinator.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating a new job.
 * POST /api/v1/jobs
 */
public record CreateJobRequest(
        @JsonProperty("jarPath") String jarPath,
        @JsonProperty("mainClass") String mainClass,
        @JsonProperty("algorithms") List<String> algorithms,
        @JsonProperty("iterations") RangeParam iterations,
        @JsonProperty("agents") RangeParam agents,
        @JsonProperty("dimension") RangeParam dimension) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Range parameter with min, max, and step values.
     */
    public record RangeParam(
            @JsonProperty("min") int min,
            @JsonProperty("max") int max,
            @JsonProperty("step") int step) {
        /** Calculate the number of values in this range */
        public int count() {
            if (step <= 0 || max < min)
                return 0;
            return ((max - min) / step) + 1;
        }

        /** Generate all values in this range */
        public List<Integer> values() {
            if (step <= 0 || max < min)
                return List.of();
            var result = new ArrayList<Integer>();
            for (int v = min; v <= max; v += step) {
                result.add(v);
            }
            return result;
        }
    }

    /** Calculate total number of tasks this job will generate */
    public int totalTasks() {
        int algCount = algorithms != null ? algorithms.size() : 0;
        int iterCount = iterations != null ? iterations.count() : 0;
        int agentCount = agents != null ? agents.count() : 0;
        int dimCount = dimension != null ? dimension.count() : 0;
        return algCount * iterCount * agentCount * dimCount;
    }

    /** Generate the job config JSON */
    public String config() {
        try {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("algorithms", algorithms);
            if (iterations != null) {
                config.put("iterations",
                        Map.of("min", iterations.min(), "max", iterations.max(), "step", iterations.step()));
            }
            if (agents != null) {
                config.put("agents", Map.of("min", agents.min(), "max", agents.max(), "step", agents.step()));
            }
            if (dimension != null) {
                config.put("dimension",
                        Map.of("min", dimension.min(), "max", dimension.max(), "step", dimension.step()));
            }
            return MAPPER.writeValueAsString(config);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Generate payloads for each task combination */
    public List<String> payloads() {
        List<String> payloads = new ArrayList<>();

        List<String> algs = algorithms != null ? algorithms : List.of();
        List<Integer> iters = iterations != null ? iterations.values() : List.of(100);
        List<Integer> agents = this.agents != null ? this.agents.values() : List.of(10);
        List<Integer> dims = dimension != null ? dimension.values() : List.of(2);

        for (String alg : algs) {
            for (int iterMax : iters) {
                for (int agent : agents) {
                    for (int dim : dims) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("alg", alg);
                        payload.put("iterations", Map.of("max", iterMax));
                        payload.put("agents", agent);
                        payload.put("dimension", dim);
                        try {
                            payloads.add(MAPPER.writeValueAsString(payload));
                        } catch (Exception e) {
                            payloads.add("{}");
                        }
                    }
                }
            }
        }

        return payloads;
    }

    /** Validate the request */
    public void validate() {
        if (jarPath == null || jarPath.isBlank()) {
            throw new IllegalArgumentException("jarPath is required");
        }
        if (mainClass == null || mainClass.isBlank()) {
            throw new IllegalArgumentException("mainClass is required");
        }
        if (algorithms == null || algorithms.isEmpty()) {
            throw new IllegalArgumentException("algorithms must not be empty");
        }
        if (iterations == null || iterations.count() == 0) {
            throw new IllegalArgumentException("iterations range is invalid");
        }
        if (agents == null || agents.count() == 0) {
            throw new IllegalArgumentException("agents range is invalid");
        }
        if (dimension == null || dimension.count() == 0) {
            throw new IllegalArgumentException("dimension range is invalid");
        }
    }
}
