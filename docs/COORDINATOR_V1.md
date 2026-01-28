# Coordinator v1 – Runbook & API Contract

## 1. Purpose & Architecture

The **Coordinator** is a central orchestration server for distributed optimization experiments. It manages:
- **Jobs**: A batch of related tasks with shared configuration
- **Tasks**: Individual work units assigned to SPOT worker nodes
- **SPOTs**: Worker nodes that register, claim tasks, and report results

### Components

```
┌─────────────────────────────────────────────────────────────┐
│                    Coordinator Server                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ RouterHandler│──│ Controllers │──│ Services            │  │
│  │ (Auth, 404) │  │ (Job, Task, │  │ (JobService,        │  │
│  └─────────────┘  │  Spot, etc) │  │  TaskService, etc)  │  │
│                   └─────────────┘  └──────────┬──────────┘  │
│  ┌─────────────────────────────────────────────┴──────────┐ │
│  │                    H2 Database (PostgreSQL mode)        │ │
│  │         Tables: jobs, tasks, spots                      │ │
│  └─────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ Scheduler: TaskReaper (stuck tasks) + SpotReaper (stale)│ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
         ▲                                    ▲
         │ /internal/v1/*                     │ /api/v1/*
    ┌────┴────┐                         ┌─────┴─────┐
    │  SPOT   │                         │  Client   │
    │ Workers │                         │  (Jobs)   │
    └─────────┘                         └───────────┘
```

### Task Lifecycle

```
NEW ──(claim)──► RUNNING ──(complete)──► DONE
                    │
                    ├──(fail + retry)──► NEW
                    │
                    └──(fail + no retry)──► FAILED
```

### Key Mechanisms

| Mechanism | Description |
|-----------|-------------|
| **TaskReaper** | Runs every 30s. Resets tasks stuck in RUNNING > 5min back to NEW (if retries remain) or FAILED |
| **SpotReaper** | Runs every 5s. Marks SPOTs with no heartbeat for 10s as DOWN, frees their tasks |
| **Retry** | Tasks have `maxAttempts` (default: 3). Each failure increments attempts |
| **Idempotency** | Complete/fail are idempotent. Duplicate requests return success without changing state |

---

## 2. API Contract

### 2.1 Public API (`/api/v1/`)

#### `GET /api/v1/health`
Health check endpoint.

**Response (200 OK):**
```json
{
  "status": "healthy",
  "database": "connected",
  "activeSpots": 3,
  "pendingTasks": 42,
  "runningTasks": 12
}
```

---

#### `GET /api/v1/spots`
List all registered SPOT workers.

**Response (200 OK):**
```json
{
  "spots": [
    {
      "id": "spot-1",
      "ipAddress": "192.168.1.100",
      "status": "UP",
      "cpuLoad": 45.2,
      "runningTasks": 2,
      "totalCores": 8,
      "lastHeartbeat": "2026-01-28T17:00:00Z"
    }
  ]
}
```

---

#### `POST /api/v1/jobs`
Create a new job with tasks.

**Request:**
```json
{
  "jarPath": "/path/to/optimizer.jar",
  "mainClass": "com.example.OptRunner",
  "algorithms": ["PSO", "GA", "DE"],
  "iterations": {"min": 100, "max": 500, "step": 100},
  "agents": {"min": 10, "max": 50, "step": 10},
  "dimension": {"min": 2, "max": 10, "step": 2}
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "jobId": "job-abc123",
  "totalTasks": 75
}
```

---

#### `GET /api/v1/jobs/{jobId}`
Get job status.

**Response (200 OK):**
```json
{
  "id": "job-abc123",
  "status": "RUNNING",
  "totalTasks": 75,
  "completedTasks": 30,
  "failedTasks": 2,
  "createdAt": "2026-01-28T16:00:00Z"
}
```

**Response (404 Not Found):**
```json
{"success": false, "error": "job not found"}
```

---

#### `GET /api/v1/jobs/{jobId}/results`
Get completed task results.

**Response (200 OK):**
```json
{
  "jobId": "job-abc123",
  "totalCompleted": 30,
  "results": [
    {
      "taskId": "task-001",
      "algorithm": "PSO",
      "iterations": 100,
      "fopt": 0.00123,
      "runtimeMs": 1500,
      "finishedAt": "2026-01-28T16:30:00Z"
    }
  ]
}
```

---

### 2.2 Internal API (`/internal/v1/`)

> **Authentication**: If `ORHESTRA_AGENT_KEY` is set, all `/internal/v1/*` requests require:
> ```
> X-Orhestra-Key: <agent-key>
> ```

#### `POST /internal/v1/hello`
Register a new SPOT worker.

**Request:**
```json
{"ipAddress": "192.168.1.100"}
```

**Response (200 OK):**
```json
{"spotId": "spot-abc123"}
```

---

#### `POST /internal/v1/heartbeat`
Send heartbeat from SPOT.

**Request:**
```json
{
  "spotId": "spot-abc123",
  "ipAddress": "192.168.1.100",
  "cpuLoad": 45.2,
  "runningTasks": 2,
  "totalCores": 8
}
```

**Response (200 OK):**
```json
{"success": true}
```

---

#### `POST /internal/v1/tasks/claim`
Claim tasks for execution.

**Request:**
```json
{"spotId": "spot-abc123", "maxTasks": 5}
```

**Response (200 OK):**
```json
{
  "tasks": [
    {"id": "task-001", "payload": "{\"alg\":\"PSO\",\"iterations\":100}"},
    {"id": "task-002", "payload": "{\"alg\":\"GA\",\"iterations\":200}"}
  ]
}
```

---

#### `POST /internal/v1/tasks/{taskId}/complete`
Report task completion (idempotent).

**Request:**
```json
{
  "spotId": "spot-abc123",
  "runtimeMs": 1500,
  "iter": 100,
  "fopt": 0.00123,
  "resultJson": "{\"converged\": true}"
}
```

**Responses:**

| Status | Condition | Body |
|--------|-----------|------|
| 200 | Completed | `{"success": true}` |
| 200 | Already done | `{"success": true, "message": "already completed"}` |
| 404 | Not found | `{"success": false, "error": "task not found"}` |
| 409 | Wrong SPOT | `{"success": false, "error": "task not assigned to this spot"}` |

---

#### `POST /internal/v1/tasks/{taskId}/fail`
Report task failure (idempotent).

**Request:**
```json
{
  "spotId": "spot-abc123",
  "error": "OutOfMemoryError",
  "retriable": true
}
```

**Responses:**

| Status | Condition | Body |
|--------|-----------|------|
| 200 | Will retry | `{"success": true, "willRetry": true}` |
| 200 | Permanent fail | `{"success": true, "willRetry": false}` |
| 200 | Already terminal | `{"success": true, "message": "already terminal"}` |
| 404 | Not found | `{"success": false, "error": "task not found"}` |
| 409 | Wrong SPOT | `{"success": false, "error": "task not assigned to this spot"}` |

---

## 3. Local Run Instructions

### Prerequisites
- **Java**: 21+
- **Maven**: 3.9+

### Run Coordinator

```bash
cd /path/to/Orhestra_Soft
mvn compile exec:java -Dexec.mainClass="orhestra.coordinator.Main"
```

Or build and run JAR:
```bash
mvn package -DskipTests
java -jar target/OrhestraV2-2.0-SNAPSHOT.jar
```

### Database Location
By default, H2 stores data at:
```
./data/orhestra.mv.db
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ORHESTRA_PORT` | 8080 | Server port |
| `ORHESTRA_DB_URL` | `jdbc:h2:file:./data/orhestra;...` | Database URL |
| `ORHESTRA_AGENT_KEY` | *(none)* | If set, SPOTs must send `X-Orhestra-Key` header |
| `ORHESTRA_MAX_ATTEMPTS` | 3 | Default max retries per task |

### Example with Auth Key
```bash
export ORHESTRA_AGENT_KEY=secret123
export ORHESTRA_PORT=9000
java -jar target/OrhestraV2-2.0-SNAPSHOT.jar
```

---

## 4. Smoke Test Script

Save as `smoke_test.sh` and run after starting the Coordinator.

```bash
#!/bin/bash
set -e

BASE="http://localhost:8080"
KEY=""  # Set to your ORHESTRA_AGENT_KEY if configured

# Helper for internal API calls
internal() {
  if [ -n "$KEY" ]; then
    curl -s -H "Content-Type: application/json" -H "X-Orhestra-Key: $KEY" "$@"
  else
    curl -s -H "Content-Type: application/json" "$@"
  fi
}

echo "=== 1. Health Check ==="
curl -s "$BASE/api/v1/health" | jq .

echo -e "\n=== 2. Create Job ==="
JOB=$(curl -s -X POST "$BASE/api/v1/jobs" \
  -H "Content-Type: application/json" \
  -d '{
    "jarPath": "/test/opt.jar",
    "mainClass": "com.test.Main",
    "algorithms": ["PSO"],
    "iterations": {"min": 100, "max": 100, "step": 100},
    "agents": {"min": 10, "max": 10, "step": 10},
    "dimension": {"min": 2, "max": 2, "step": 1}
  }')
echo "$JOB" | jq .
JOB_ID=$(echo "$JOB" | jq -r '.jobId')

echo -e "\n=== 3. Register SPOT (hello) ==="
SPOT=$(internal -X POST "$BASE/internal/v1/hello" -d '{"ipAddress":"127.0.0.1"}')
echo "$SPOT" | jq .
SPOT_ID=$(echo "$SPOT" | jq -r '.spotId')

echo -e "\n=== 4. Heartbeat ==="
internal -X POST "$BASE/internal/v1/heartbeat" \
  -d "{\"spotId\":\"$SPOT_ID\",\"ipAddress\":\"127.0.0.1\",\"cpuLoad\":10.5,\"runningTasks\":0,\"totalCores\":4}" | jq .

echo -e "\n=== 5. Claim Tasks ==="
TASKS=$(internal -X POST "$BASE/internal/v1/tasks/claim" -d "{\"spotId\":\"$SPOT_ID\",\"maxTasks\":10}")
echo "$TASKS" | jq .
TASK_ID=$(echo "$TASKS" | jq -r '.tasks[0].id')

echo -e "\n=== 6. Complete Task ==="
internal -X POST "$BASE/internal/v1/tasks/$TASK_ID/complete" \
  -d "{\"spotId\":\"$SPOT_ID\",\"runtimeMs\":1234,\"iter\":100,\"fopt\":0.001,\"resultJson\":\"{\\\"done\\\":true}\"}" | jq .

echo -e "\n=== 7. Check Job Status ==="
curl -s "$BASE/api/v1/jobs/$JOB_ID" | jq .

echo -e "\n=== 8. Get Results ==="
curl -s "$BASE/api/v1/jobs/$JOB_ID/results" | jq .

echo -e "\n=== 9. List SPOTs ==="
curl -s "$BASE/api/v1/spots" | jq .

echo -e "\n✅ Smoke test complete!"
```

---

## 5. Troubleshooting

### Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| **401 Unauthorized** | Missing/wrong `X-Orhestra-Key` | Add header: `-H "X-Orhestra-Key: <key>"` |
| **404 Not Found** | Unknown endpoint or resource | Check path spelling, verify job/task exists |
| **409 WRONG_SPOT** | SPOT tried to complete task assigned to another | Each SPOT should only complete tasks it claimed |
| **Task stuck in RUNNING** | SPOT crashed before completing | Wait for TaskReaper (30s interval, 5min threshold) to reset it |

### Inspect Database

Connect to H2 console (if running embedded):
```bash
java -jar ~/.m2/repository/com/h2database/h2/2.*/h2*.jar \
  -url "jdbc:h2:./data/orhestra" -user "" -password ""
```

Or use SQL via code:
```sql
-- Check stuck tasks
SELECT id, status, assigned_to, started_at FROM tasks WHERE status = 'RUNNING';

-- Check job progress
SELECT id, status, total_tasks, completed_tasks, failed_tasks FROM jobs;

-- Check SPOT health
SELECT id, status, last_heartbeat FROM spots ORDER BY last_heartbeat DESC;
```

### Check Logs

Logs are printed to stdout. Key log patterns:
```
INFO  TaskReaper - Reaped task task-xxx for retry
WARN  TaskReaper - Task task-xxx permanently failed after 3 attempts
INFO  SpotService - Reaped 2 stale SPOTs, freed 5 tasks
WARN  JdbcTaskRepository - Spot Y tried to complete task X but it's assigned to Z
```

### Force Reset Stuck Task

If a task is genuinely stuck and won't auto-recover:
```sql
UPDATE tasks SET status = 'NEW', assigned_to = NULL, started_at = NULL WHERE id = 'task-xxx';
```
