package orhestra.coordinator.api.v1.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates flat JSON task payloads from a dynamic parameter specification
 * via Cartesian-product expansion.
 *
 * <p>Output per task:
 * <pre>
 * {
 *   "artifactBucket":   "...",
 *   "artifactKey":      "...",
 *   "artifactEndpoint": "...",
 *   "mainClass":        "...",
 *   "params": {
 *     "group.paramId": <value>,
 *     ...
 *   }
 * }
 * </pre>
 */
public final class PayloadGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PayloadGenerator() {}

    /**
     * Generate one JSON payload string per unique parameter combination.
     *
     * @param artifactBucket   S3 bucket name
     * @param artifactKey      S3 object key
     * @param artifactEndpoint S3/MinIO endpoint URL
     * @param mainClass        fully-qualified main class name
     * @param groups           parameter group specifications
     * @return list of JSON payload strings (one per task)
     */
    public static List<String> generate(
            String artifactBucket,
            String artifactKey,
            String artifactEndpoint,
            String mainClass,
            List<CreateJobRequest.ParameterGroupRequest> groups) {

        List<Map.Entry<String, List<Object>>> allParams = flattenParams(groups);
        List<Map<String, Object>> combinations = cartesian(allParams);

        List<String> payloads = new ArrayList<>(combinations.size());
        for (Map<String, Object> combo : combinations) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("artifactBucket",   artifactBucket);
            payload.put("artifactKey",      artifactKey);
            payload.put("artifactEndpoint", artifactEndpoint);
            payload.put("mainClass",        mainClass);
            payload.put("params",           combo);
            try {
                payloads.add(MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                payloads.add("{}");
            }
        }
        return payloads;
    }

    /**
     * Count the total number of tasks that will be generated without
     * materialising the full payload list.
     */
    public static int countTasks(List<CreateJobRequest.ParameterGroupRequest> groups) {
        List<Map.Entry<String, List<Object>>> flat = flattenParams(groups);
        if (flat.isEmpty()) return 0;
        int count = 1;
        for (Map.Entry<String, List<Object>> entry : flat) {
            count *= entry.getValue().size();
        }
        return count;
    }

    // --- helpers ---

    private static List<Map.Entry<String, List<Object>>> flattenParams(
            List<CreateJobRequest.ParameterGroupRequest> groups) {

        List<Map.Entry<String, List<Object>>> result = new ArrayList<>();
        if (groups == null) return result;

        for (CreateJobRequest.ParameterGroupRequest group : groups) {
            if (group.params() == null) continue;
            for (Map.Entry<String, ParameterValue> entry : group.params().entrySet()) {
                String key = group.groupId() + "." + entry.getKey();
                List<Object> vals = entry.getValue().expand();
                if (!vals.isEmpty()) {
                    result.add(Map.entry(key, vals));
                }
            }
        }
        return result;
    }

    private static List<Map<String, Object>> cartesian(List<Map.Entry<String, List<Object>>> params) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(new LinkedHashMap<>());

        for (Map.Entry<String, List<Object>> param : params) {
            String key = param.getKey();
            List<Object> vals = param.getValue();
            List<Map<String, Object>> next = new ArrayList<>(result.size() * vals.size());
            for (Map<String, Object> existing : result) {
                for (Object val : vals) {
                    Map<String, Object> combo = new LinkedHashMap<>(existing);
                    combo.put(key, val);
                    next.add(combo);
                }
            }
            result = next;
        }
        return result;
    }
}
