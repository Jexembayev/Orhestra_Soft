package orhestra.coordinator.service;

import orhestra.coordinator.config.CoordinatorConfig;
import orhestra.coordinator.model.*;
import orhestra.coordinator.repository.JobRepository;
import orhestra.coordinator.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for Job management.
 * Handles job creation, task generation, and status tracking.
 */
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final TaskRepository taskRepository;
    private final CoordinatorConfig config;

    public JobService(JobRepository jobRepository, TaskRepository taskRepository, CoordinatorConfig config) {
        this.jobRepository = jobRepository;
        this.taskRepository = taskRepository;
        this.config = config;
    }

    /**
     * Create a new job and generate its tasks.
     *
     * @param jarPath   path to the JAR file
     * @param mainClass main class name
     * @param config    job configuration JSON
     * @param payloads  list of task payloads
     * @return created job
     */
    public Job createJob(String jarPath, String mainClass, String config, List<String> payloads) {
        String jobId = jobRepository.generateId();

        Job job = Job.builder()
                .id(jobId)
                .jarPath(jarPath)
                .mainClass(mainClass)
                .config(config)
                .status(JobStatus.PENDING)
                .totalTasks(payloads.size())
                .completedTasks(0)
                .failedTasks(0)
                .createdAt(Instant.now())
                .build();

        jobRepository.save(job);
        log.info("Created job {} with {} tasks", jobId, payloads.size());

        // Create tasks for this job
        List<Task> tasks = new ArrayList<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (int i = 0; i < payloads.size(); i++) {
            String taskId = UUID.randomUUID().toString();
            String payloadStr = payloads.get(i);

            // Extract input parameters from payload
            String alg = null;
            Integer inputIter = null;
            Integer inputAgents = null;
            Integer inputDim = null;
            try {
                var root = mapper.readTree(payloadStr);
                if (root.has("alg") && !root.get("alg").isNull())
                    alg = root.get("alg").asText();
                var iterNode = root.path("iterations");
                if (iterNode.isObject() && iterNode.has("max"))
                    inputIter = iterNode.get("max").asInt();
                else if (iterNode.isNumber())
                    inputIter = iterNode.asInt();
                if (root.has("agents") && root.get("agents").isNumber())
                    inputAgents = root.get("agents").asInt();
                if (root.has("dimension") && root.get("dimension").isNumber())
                    inputDim = root.get("dimension").asInt();
            } catch (Exception ignored) {
            }

            Task task = Task.builder()
                    .id(taskId)
                    .jobId(jobId)
                    .payload(payloadStr)
                    .status(TaskStatus.NEW)
                    .priority(i) // Earlier tasks have lower priority (process in order)
                    .maxAttempts(this.config.defaultMaxAttempts())
                    .createdAt(Instant.now())
                    .algorithm(alg)
                    .inputIterations(inputIter)
                    .inputAgents(inputAgents)
                    .inputDimension(inputDim)
                    .build();
            tasks.add(task);
        }

        // Batch save tasks
        for (Task task : tasks) {
            taskRepository.save(task);
        }

        log.debug("Created {} tasks for job {}", tasks.size(), jobId);
        return job;
    }

    /**
     * Find a job by ID.
     */
    public Optional<Job> findById(String jobId) {
        return jobRepository.findById(jobId);
    }

    /**
     * Get all jobs, most recent first.
     */
    public List<Job> findAll() {
        return jobRepository.findAll();
    }

    /**
     * Get recent jobs.
     */
    public List<Job> findRecent(int limit) {
        return jobRepository.findRecent(limit);
    }

    /**
     * Get tasks for a job that have completed (DONE status).
     */
    public List<Task> getCompletedTasks(String jobId) {
        return taskRepository.findByJobId(jobId).stream()
                .filter(t -> t.status() == TaskStatus.DONE)
                .toList();
    }

    /**
     * Get all tasks for a job.
     */
    public List<Task> getTasks(String jobId) {
        return taskRepository.findByJobId(jobId);
    }

    /**
     * Cancel a job and all its pending tasks.
     */
    public boolean cancel(String jobId) {
        Optional<Job> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return false;
        }

        Job job = jobOpt.get();
        if (job.isTerminal()) {
            log.warn("Cannot cancel job {} - already in terminal state: {}", jobId, job.status());
            return false;
        }

        // Cancel pending tasks
        List<Task> tasks = taskRepository.findByJobId(jobId);
        for (Task task : tasks) {
            if (task.status() == TaskStatus.NEW) {
                taskRepository.updateStatus(task.id(), TaskStatus.CANCELLED);
            }
        }

        // Mark job as cancelled
        jobRepository.markFinished(jobId, JobStatus.CANCELLED);
        log.info("Cancelled job {}", jobId);
        return true;
    }

    /**
     * Called when a task completes to update job progress.
     */
    public void onTaskCompleted(String jobId) {
        jobRepository.incrementCompleted(jobId);
        checkJobCompletion(jobId);
    }

    /**
     * Called when a task fails permanently to update job progress.
     */
    public void onTaskFailed(String jobId) {
        jobRepository.incrementFailed(jobId);
        checkJobCompletion(jobId);
    }

    /**
     * Check if job is complete (all tasks done or failed).
     */
    private void checkJobCompletion(String jobId) {
        Optional<Job> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return;
        }

        Job job = jobOpt.get();
        int processed = job.completedTasks() + job.failedTasks();

        if (processed >= job.totalTasks()) {
            // Job is complete
            JobStatus finalStatus = job.failedTasks() == 0 ? JobStatus.COMPLETED : JobStatus.FAILED;
            jobRepository.markFinished(jobId, finalStatus);
            log.info("Job {} finished: {} (completed={}, failed={})",
                    jobId, finalStatus, job.completedTasks(), job.failedTasks());
        }
    }
}
