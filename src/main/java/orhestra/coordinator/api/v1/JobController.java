package orhestra.coordinator.api.v1;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import orhestra.coordinator.api.Controller;
import orhestra.coordinator.api.v1.dto.CreateJobRequest;
import orhestra.coordinator.api.v1.dto.JobResponse;
import orhestra.coordinator.api.v1.dto.TaskResultResponse;
import orhestra.coordinator.model.Job;
import orhestra.coordinator.model.Task;
import orhestra.coordinator.server.RouterHandler;
import orhestra.coordinator.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controller for Job management (public API).
 * 
 * POST /api/v1/jobs - Create a new job
 * GET /api/v1/jobs/{jobId} - Get job status
 * GET /api/v1/jobs/{jobId}/results - Get job results
 */
public class JobController implements Controller {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private static final Pattern JOBS_PATTERN = Pattern.compile("^/api/v1/jobs$");
    private static final Pattern JOB_BY_ID_PATTERN = Pattern.compile("^/api/v1/jobs/([^/]+)$");
    private static final Pattern JOB_RESULTS_PATTERN = Pattern.compile("^/api/v1/jobs/([^/]+)/results$");

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public boolean matches(HttpMethod method, String path) {
        if (method.equals(HttpMethod.POST) && JOBS_PATTERN.matcher(path).matches()) {
            return true;
        }
        if (method.equals(HttpMethod.GET)) {
            return JOB_BY_ID_PATTERN.matcher(path).matches() ||
                    JOB_RESULTS_PATTERN.matcher(path).matches();
        }
        return false;
    }

    @Override
    public ControllerResponse handle(ChannelHandlerContext ctx, FullHttpRequest req, String path) {
        try {
            if (req.method().equals(HttpMethod.POST) && JOBS_PATTERN.matcher(path).matches()) {
                return handleCreateJob(req);
            }

            Matcher resultsMatcher = JOB_RESULTS_PATTERN.matcher(path);
            if (req.method().equals(HttpMethod.GET) && resultsMatcher.matches()) {
                String jobId = resultsMatcher.group(1);
                return handleGetResults(jobId);
            }

            Matcher jobMatcher = JOB_BY_ID_PATTERN.matcher(path);
            if (req.method().equals(HttpMethod.GET) && jobMatcher.matches()) {
                String jobId = jobMatcher.group(1);
                return handleGetJob(jobId);
            }

            return ControllerResponse.notFound("unknown job endpoint");

        } catch (IllegalArgumentException e) {
            return ControllerResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Job controller error", e);
            return ControllerResponse.error("internal error");
        }
    }

    /**
     * POST /api/v1/jobs - Create a new job
     */
    private ControllerResponse handleCreateJob(FullHttpRequest req) throws Exception {
        String body = req.content().toString(StandardCharsets.UTF_8);
        CreateJobRequest request = RouterHandler.mapper().readValue(body, CreateJobRequest.class);

        // Validate
        request.validate();

        // Create job
        Job job = jobService.createJob(
                request.jarPath(),
                request.mainClass(),
                request.config(),
                request.payloads());

        Map<String, Object> response = Map.of(
                "success", true,
                "jobId", job.id(),
                "totalTasks", job.totalTasks());

        return ControllerResponse.json(
                HttpResponseStatus.CREATED,
                RouterHandler.mapper().writeValueAsString(response));
    }

    /**
     * GET /api/v1/jobs/{jobId} - Get job status
     */
    private ControllerResponse handleGetJob(String jobId) throws Exception {
        Optional<Job> jobOpt = jobService.findById(jobId);

        if (jobOpt.isEmpty()) {
            return ControllerResponse.json(
                    HttpResponseStatus.NOT_FOUND,
                    "{\"success\":false,\"error\":\"job not found\"}");
        }

        Job job = jobOpt.get();
        JobResponse response = JobResponse.from(job);

        return ControllerResponse.json(RouterHandler.mapper().writeValueAsString(response));
    }

    /**
     * GET /api/v1/jobs/{jobId}/results - Get job results
     */
    private ControllerResponse handleGetResults(String jobId) throws Exception {
        Optional<Job> jobOpt = jobService.findById(jobId);

        if (jobOpt.isEmpty()) {
            return ControllerResponse.json(
                    HttpResponseStatus.NOT_FOUND,
                    "{\"success\":false,\"error\":\"job not found\"}");
        }

        List<Task> completedTasks = jobService.getCompletedTasks(jobId);

        List<TaskResultResponse> results = completedTasks.stream()
                .map(TaskResultResponse::from)
                .toList();

        Map<String, Object> response = Map.of(
                "jobId", jobId,
                "totalCompleted", results.size(),
                "results", results);

        return ControllerResponse.json(RouterHandler.mapper().writeValueAsString(response));
    }
}
