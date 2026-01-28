package orhestra.coordinator.api.v1.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreateJobRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializeFromJson() throws Exception {
        String json = """
                {
                  "jarPath": "s3://bucket/algo.jar",
                  "mainClass": "algorithms.Main",
                  "algorithms": ["sphere", "rosenbrock"],
                  "iterations": { "min": 100, "max": 300, "step": 100 },
                  "agents":     { "min": 25,  "max": 75,  "step": 25 },
                  "dimension":  { "min": 1,   "max": 2,   "step": 1 }
                }
                """;

        CreateJobRequest req = mapper.readValue(json, CreateJobRequest.class);

        assertEquals("s3://bucket/algo.jar", req.jarPath());
        assertEquals("algorithms.Main", req.mainClass());
        assertEquals(List.of("sphere", "rosenbrock"), req.algorithms());

        assertEquals(100, req.iterations().min());
        assertEquals(300, req.iterations().max());
        assertEquals(100, req.iterations().step());
    }

    @Test
    void rangeParamCount() {
        var range = new CreateJobRequest.RangeParam(100, 300, 100);
        assertEquals(3, range.count()); // 100, 200, 300

        var single = new CreateJobRequest.RangeParam(10, 10, 1);
        assertEquals(1, single.count()); // just 10

        var invalid = new CreateJobRequest.RangeParam(10, 5, 1);
        assertEquals(0, invalid.count()); // max < min
    }

    @Test
    void rangeParamValues() {
        var range = new CreateJobRequest.RangeParam(25, 75, 25);
        assertEquals(List.of(25, 50, 75), range.values());
    }

    @Test
    void totalTasks() throws Exception {
        String json = """
                {
                  "jarPath": "s3://bucket/algo.jar",
                  "mainClass": "Main",
                  "algorithms": ["sphere", "rosenbrock"],
                  "iterations": { "min": 100, "max": 300, "step": 100 },
                  "agents":     { "min": 25,  "max": 75,  "step": 25 },
                  "dimension":  { "min": 1,   "max": 2,   "step": 1 }
                }
                """;

        CreateJobRequest req = mapper.readValue(json, CreateJobRequest.class);

        // 2 algorithms * 3 iterations * 3 agents * 2 dimensions = 36 tasks
        assertEquals(36, req.totalTasks());
    }

    @Test
    void validateMissingJarPath() {
        CreateJobRequest req = new CreateJobRequest(
                null, "Main", List.of("sphere"),
                new CreateJobRequest.RangeParam(1, 1, 1),
                new CreateJobRequest.RangeParam(1, 1, 1),
                new CreateJobRequest.RangeParam(1, 1, 1));

        assertThrows(IllegalArgumentException.class, req::validate);
    }

    @Test
    void validateEmptyAlgorithms() {
        CreateJobRequest req = new CreateJobRequest(
                "s3://jar", "Main", List.of(),
                new CreateJobRequest.RangeParam(1, 1, 1),
                new CreateJobRequest.RangeParam(1, 1, 1),
                new CreateJobRequest.RangeParam(1, 1, 1));

        assertThrows(IllegalArgumentException.class, req::validate);
    }
}
