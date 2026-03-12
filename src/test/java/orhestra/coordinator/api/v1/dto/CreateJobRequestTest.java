package orhestra.coordinator.api.v1.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CreateJobRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializeFromJson() throws Exception {
        String json = """
                {
                  "artifactBucket":   "orhestra-algorithms",
                  "artifactKey":      "experiments/algo.jar",
                  "artifactEndpoint": "http://localhost:9000",
                  "mainClass":        "com.example.Main",
                  "parameters": [
                    {
                      "groupId": "algorithm",
                      "params": {
                        "name": { "type": "ENUM_LIST", "values": ["DE", "PSO"] }
                      }
                    }
                  ]
                }
                """;

        CreateJobRequest req = mapper.readValue(json, CreateJobRequest.class);

        assertEquals("orhestra-algorithms", req.artifactBucket());
        assertEquals("experiments/algo.jar", req.artifactKey());
        assertEquals("http://localhost:9000", req.artifactEndpoint());
        assertEquals("com.example.Main", req.mainClass());
        assertEquals(1, req.parameters().size());
        assertEquals("algorithm", req.parameters().get(0).groupId());
    }

    @Test
    void parameterValueIntRangeExpands() {
        ParameterValue pv = new ParameterValue("INT_RANGE", 100, 300, 100, null, null);
        List<Object> vals = pv.expand();
        assertEquals(List.of(100, 200, 300), vals);
    }

    @Test
    void parameterValueConstantExpands() {
        ParameterValue pv = new ParameterValue("CONSTANT", null, null, null, 42, null);
        assertEquals(List.of(42), pv.expand());
    }

    @Test
    void parameterValueEnumListExpands() {
        ParameterValue pv = new ParameterValue("ENUM_LIST", null, null, null, null, List.of("DE", "PSO", "GWO"));
        assertEquals(List.of("DE", "PSO", "GWO"), pv.expand());
    }

    @Test
    void totalTasksCartesian() throws Exception {
        String json = """
                {
                  "artifactBucket": "bucket",
                  "artifactKey":    "algo.jar",
                  "mainClass":      "Main",
                  "parameters": [
                    {
                      "groupId": "algorithm",
                      "params": {
                        "name": { "type": "ENUM_LIST", "values": ["DE", "PSO"] }
                      }
                    },
                    {
                      "groupId": "run",
                      "params": {
                        "iterations": { "type": "INT_RANGE", "min": 100, "max": 200, "step": 100 },
                        "agents":     { "type": "CONSTANT",  "value": 10 },
                        "dimension":  { "type": "CONSTANT",  "value": 2  }
                      }
                    }
                  ]
                }
                """;

        CreateJobRequest req = mapper.readValue(json, CreateJobRequest.class);
        // 2 algs × 2 iter values × 1 × 1 = 4
        assertEquals(4, req.totalTasks());
    }

    @Test
    void payloadsContainArtifactAndParams() throws Exception {
        String json = """
                {
                  "artifactBucket":   "my-bucket",
                  "artifactKey":      "my.jar",
                  "artifactEndpoint": "http://s3:9000",
                  "mainClass":        "com.Main",
                  "parameters": [
                    {
                      "groupId": "run",
                      "params": {
                        "iter": { "type": "CONSTANT", "value": 100 }
                      }
                    }
                  ]
                }
                """;

        CreateJobRequest req = mapper.readValue(json, CreateJobRequest.class);
        List<String> payloads = req.payloads();
        assertEquals(1, payloads.size());

        var node = mapper.readTree(payloads.get(0));
        assertEquals("my-bucket", node.get("artifactBucket").asText());
        assertEquals("my.jar",    node.get("artifactKey").asText());
        assertEquals("com.Main",  node.get("mainClass").asText());
        assertEquals(100, node.path("params").path("run.iter").asInt());
    }

    @Test
    void validateMissingArtifactBucket() {
        CreateJobRequest req = new CreateJobRequest(
                null, "key.jar", "http://s3", "Main",
                List.of(new CreateJobRequest.ParameterGroupRequest("g", Map.of(
                        "x", new ParameterValue("CONSTANT", null, null, null, 1, null)))));
        assertThrows(IllegalArgumentException.class, req::validate);
    }

    @Test
    void validateMissingMainClass() {
        CreateJobRequest req = new CreateJobRequest(
                "bucket", "key.jar", "http://s3", null,
                List.of(new CreateJobRequest.ParameterGroupRequest("g", Map.of(
                        "x", new ParameterValue("CONSTANT", null, null, null, 1, null)))));
        assertThrows(IllegalArgumentException.class, req::validate);
    }

    @Test
    void validateEmptyParameters() {
        CreateJobRequest req = new CreateJobRequest(
                "bucket", "key.jar", "http://s3", "Main", List.of());
        assertThrows(IllegalArgumentException.class, req::validate);
    }
}
