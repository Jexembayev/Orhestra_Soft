package orhestra.coordinator.repository;

import orhestra.coordinator.model.Job;
import orhestra.coordinator.model.JobStatus;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Job persistence.
 */
public interface JobRepository {

    /**
     * Save a new job.
     * 
     * @param job the job to save
     */
    void save(Job job);

    /**
     * Find a job by ID.
     * 
     * @param jobId the job ID
     * @return the job if found
     */
    Optional<Job> findById(String jobId);

    /**
     * Get all jobs.
     * 
     * @return list of all jobs
     */
    List<Job> findAll();

    /**
     * Get jobs by status.
     * 
     * @param status the status filter
     * @return list of jobs
     */
    List<Job> findByStatus(JobStatus status);

    /**
     * Get recent jobs ordered by creation time.
     * 
     * @param limit maximum results
     * @return list of jobs
     */
    List<Job> findRecent(int limit);

    /**
     * Update job status.
     * 
     * @param jobId  the job ID
     * @param status the new status
     * @return true if updated
     */
    boolean updateStatus(String jobId, JobStatus status);

    /**
     * Increment completed task count for a job.
     * 
     * @param jobId the job ID
     * @return new completed count
     */
    int incrementCompleted(String jobId);

    /**
     * Increment failed task count for a job.
     * 
     * @param jobId the job ID
     * @return new failed count
     */
    int incrementFailed(String jobId);

    /**
     * Update job to RUNNING status and set startedAt if not already set.
     * 
     * @param jobId the job ID
     */
    void markStarted(String jobId);

    /**
     * Update job to terminal status (COMPLETED or FAILED) and set finishedAt.
     * 
     * @param jobId  the job ID
     * @param status the terminal status
     */
    void markFinished(String jobId, JobStatus status);

    /**
     * Delete a job and all its tasks.
     * 
     * @param jobId the job ID
     * @return true if deleted
     */
    boolean delete(String jobId);

    /**
     * Generate a new unique Job ID.
     * 
     * @return unique ID like "job-{uuid}"
     */
    String generateId();
}
