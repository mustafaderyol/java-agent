# System & JVM Resource Metrics Guide

The Java Agent features an autonomous, low-overhead System & JVM Metrics reporting module that periodically collects resource consumption statistics for both the **Host Machine** and the **monitored JVM process**.

---

## ⚡ Key Highlights & Safety Guarantees

- **Zero Risk of Out-of-Memory (OOM)**:
  - Metrics are read using standard JDK `ManagementFactory` MXBeans.
  - No unbounded queues or memory structures are used.
  - The metrics log rotates automatically at `metricsMaxFileSizeBytes` (default: 10MB) keeping `metricsMaxBackupFiles` (default: 3).
- **Near-Zero CPU Impact**:
  - Sampled using a single dedicated background daemon thread (`JavaAgent-MetricsLogger`) configured with minimum thread priority (`Thread.MIN_PRIORITY`).
  - Configurable sampling interval (default: 60 seconds) so it never causes jitter or CPU spikes.
- **Dual Exposure**:
  - **Rotating NDJSON File**: Written directly to `agent-logs/metrics-<jvmId>.log` (or `metrics.log`). Ideal for Filebeat, Promtail, FluentBit, Logstash, Datadog Agent, or AWS CloudWatch.
  - **REST API Endpoint (`GET /metrics`)**: Returns the latest in-memory cached snapshot instantly (0 ms latency).
  - **Live Web Dashboard Ribbon**: Integrated directly into `http://localhost:9876/ui` with real-time CPU, Heap, RAM, Threads, GC, and Disk visualizers.

---

## 📊 Metrics Schema (NDJSON & REST API)

Each periodic line in the metrics log is a valid JSON object matching the following schema:

```json
{
  "timestamp": "2026-10-25T00:23:00.120Z",
  "timestampEpochMs": 1729808580120,
  "host": "production-worker-01",
  "jvmId": "order-srv-node1",
  "pid": 58312,
  "uptimeSeconds": 1420,
  "jvm": {
    "heapUsedBytes": 134217728,
    "heapCommittedBytes": 536870912,
    "heapMaxBytes": 2147483648,
    "heapUsagePercent": 6.25,
    "nonHeapUsedBytes": 48234496,
    "nonHeapCommittedBytes": 52428800,
    "liveThreadCount": 28,
    "peakThreadCount": 35,
    "daemonThreadCount": 19,
    "gcCollectionCount": 12,
    "gcCollectionTimeMs": 84,
    "processCpuLoadPercent": 1.2
  },
  "system": {
    "systemCpuLoadPercent": 4.8,
    "systemLoadAverage": 1.45,
    "availableProcessors": 8,
    "totalPhysicalMemoryBytes": 17179869184,
    "freePhysicalMemoryBytes": 8589934592,
    "usedPhysicalMemoryBytes": 8589934592,
    "physicalMemoryUsagePercent": 50.0,
    "totalDiskBytes": 500107862016,
    "usableDiskBytes": 320456122368,
    "diskUsagePercent": 35.92
  },
  "agent": {
    "loggedTraceEvents": 14200,
    "droppedTraceEvents": 0,
    "queuePendingEvents": 3,
    "activeProbesCount": 1
  }
}
```

---

## ⚙️ Configuration Properties (`agent-config.json`)

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `metricsEnabled` | `boolean` | `true` | Master switch for periodic host & JVM resource logging. |
| `metricsIntervalSeconds` | `int` | `60` | Sampling period in seconds (minimum 5s). |
| `metricsFileName` | `String` | `"metrics.log"` | Base filename (in `logDirectory`). Automatically adopts `{jvmId}` in multi-instance environments. |
| `metricsMaxFileSizeBytes`| `long` | `10485760` (10MB) | File size threshold to trigger log rotation. |
| `metricsMaxBackupFiles` | `int` | `3` | Number of rotated backup files retained (`.1`, `.2`, `.3`). |

### Command-Line Overrides
```bash
java -javaagent:target/javaagent-2.0.0.jar=metricsEnabled=true,metricsIntervalSeconds=30 \
     -jar your-app.jar
```

---

## 📡 REST API Usage

### Query Latest Metrics Snapshot
```bash
curl -s http://localhost:9876/metrics | jq .
```
