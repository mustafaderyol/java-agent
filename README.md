# Enterprise Java Tracing & Dynamic Probe Agent

[![Java Version](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Version](https://img.shields.io/badge/version-2.0.0-success.svg)](https://github.com/mustafaderyol/java-agent/releases/tag/v2.0.0)
[![ByteBuddy](https://img.shields.io/badge/ByteBuddy-1.15.7-orange.svg)](https://bytebuddy.net/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Overhead](https://img.shields.io/badge/Runtime%20Overhead-%3C%202%25-brightgreen.svg)](#performance--benchmarks)

High-performance, zero-blocking distributed tracing and runtime dynamic parameter/return-value probe agent for enterprise JVM environments.

---

## ⚡ Overview

Monitoring high-throughput microservices in production presents a classic dilemma:
- **Tracing only execution times** leaves engineers guessing what input parameters caused an intermittent bug.
- **Logging all arguments and responses** floods disks, burns CPU cycles on JSON serialization, and causes severe application latency.

The **Enterprise Java Agent** resolves this dilemma through a dual-mode approach:
1. **Always-On Zero-Overhead Distributed Tracing**: Emits microsecond-accurate spans, call trees, and error indicators through a lock-free, asynchronous ring-buffer (`ArrayBlockingQueue`) with guaranteed `ThreadLocal` cleanup.
2. **On-Demand Dynamic Probes (Zero-Restart)**: Connect via HTTP or the embedded web dashboard at runtime to attach live payload probes to any method. Downstream service calls automatically inherit the probe flag, capturing exact request and response payloads with auto-expiration limits.

---

## 🏗️ Architecture

```mermaid
flowchart TD
    subgraph Host_JVM ["Monitored JVM Runtime"]
        subgraph ByteBuddy ["ByteBuddy Instrumentation (@Advice)"]
            Advice["MethodTraceAdvice (Inlined Bytecode)"]
            ProbeCheck["Dynamic Probe Check"]
        end

        subgraph Context ["Concurrency & Context Management"]
            TL["ThreadLocal<TraceContext>"]
            TraceState["Trace ID / Span ID / Parent Span ID / Depth"]
            TreeFlag["ProbeCaptureActive Flag (Tree Inheritance)"]
        end

        subgraph Ingestion ["Non-Blocking Logging Ring Buffer"]
            Queue["ArrayBlockingQueue<TraceEvent> (Lock-free batching)"]
            Worker["Async Worker Thread (Daemon)"]
        end

        subgraph ProbeEngine ["Dynamic Probe Engine"]
            ProbeMgr["DynamicProbeManager (ConcurrentHashMap)"]
            ProbeBuf["CircularFifoQueue<ProbeCaptureEvent> (In-Memory)"]
            ProbeLog["ProbeCaptureLogger (probe-capture-*.log)"]
        end

        subgraph Management ["Embedded Management HTTP Server"]
            HttpSrv["ProbeServer (Port: 9876)"]
            WebUI["Embedded SPA Dashboard (/ui)"]
        end
    end

    subgraph Storage ["Persistent Telemetry Filesystem"]
        TraceFiles["agent-logs/trace.log (Rotated at 20MB)"]
        ProbeFiles["agent-logs/probe-capture-*.log"]
    end

    Advice -->|1. Enter: Start Span| TL
    Advice -->|2. Check Probe Key| ProbeCheck
    ProbeCheck -.->|Query Active Probes| ProbeMgr
    TL -.->|Inherits Downstream| ProbeCheck

    Advice -->|3. Exit: Offer Event| Queue
    ProbeCheck -->|Capture Args / Return| ProbeMgr
    ProbeMgr -->|Store In-Memory| ProbeBuf
    ProbeMgr -->|Async Flush| ProbeLog
    ProbeLog --> ProbeFiles

    Queue -->|Drain Batches| Worker
    Worker -->|Buffered Write| TraceFiles

    HttpSrv -->|REST API| ProbeMgr
    HttpSrv -->|Serve UI| WebUI
```

Detailed design and internals: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## 🚀 Key Features

- **Hierarchical Distributed Call Trees**: Reconstructs "Who called whom" with `traceId`, `spanId`, `parentSpanId`, and `depth`. Supports W3C `traceparent` and `X-Transaction-Id` propagation across HTTP, REST, and SOAP services.
- **Dynamic On-Demand Probing**: Attach parameter (`args`) and return value (`result`) probes to any class or method at runtime via REST cURL or the web UI. No restarts required.
- **Downstream Tree Inheritance**: When a probe is attached to an entry point (e.g., an Order Controller or SOAP endpoint), every downstream internal method automatically inherits the capture flag for that transaction.
- **Zero-Leak Memory Safety**:
  - Root method completion unconditionally executes `ThreadLocal.remove()`, preventing memory leaks in Tomcat, Jetty, and Undertow thread pools.
  - Bounded ring buffer with drop policy protects against unbounded RAM consumption during traffic bursts.
  - Recursion depth caps (`maxCallDepth: 64`) and string length truncation prevent stack overflows and memory exhaustion.
- **Multi-JVM & Cloud Isolation**: Automatic or configurable `jvmId`, Process PID, and Hostname tagging ensure isolated log streams even when multiple JVMs execute on the same host or Kubernetes node.
- **Periodic System & JVM Resource Metrics**: Autonomous, ultra-low-overhead background logger sampling Host CPU, JVM CPU, Heap Memory, Host RAM, Active Threads, Garbage Collection metrics, and Disk Space into rotating NDJSON files (`agent-logs/metrics-*.log`) and exposing live data via `GET /metrics`.
- **Environment Variables & System Properties Inspection**: On-demand logging and HTTP endpoint (`GET /env`) with automatic sensitive data masking (`password`, `secret`, `token`, `key`).
- **Spring Framework Bean Discovery**: Automatic detection of Spring Boot `ApplicationContext` via non-invasive reflection, with REST API (`GET /beans`), startup file logging, and 1-click dynamic method probing directly from the Web UI.
- **Embedded Web Dashboard**: Self-contained single-page dashboard served directly by the agent on `http://localhost:9876/ui` with live system health indicators, interactive Spring Beans explorer, environment inspector, search, trace trees, and probe controls.

---

## 📦 Getting Started

### 1. Build the Agent
```bash
mvn clean package
```
Artifact generated: `target/javaagent-2.0.0.jar`

### 2. Attach to Your Java Application

#### Recommended: External Config File
```bash
java -javaagent:/path/to/javaagent-2.0.0.jar=config=agent-config.json -jar your-app.jar
```

#### Command-Line Overrides
```bash
java -javaagent:target/javaagent-2.0.0.jar=jvmId=order-srv-1,probePort=9876,basePackages=com.mycompany.* \
     -jar your-app.jar
```

#### Kubernetes / Docker
```dockerfile
COPY target/javaagent-2.0.0.jar /opt/agent/javaagent.jar
COPY agent-config.json /opt/agent/agent-config.json
ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/agent/javaagent.jar=config=/opt/agent/agent-config.json"
```

---

## 🖥️ Embedded Web Dashboard

Open your browser at:
```
http://localhost:9876/ui
```

### Dashboard Features
- **Real-Time Trace Visualizer**: Filter and inspect call traces by Status (Success/Error), Class, Method, or Latency.
- **Interactive Call Trees**: Visual tree diagram with microsecond breakdown, depth badges, and root span indicators.
- **Live Probe Controller**: View active probes, capture counts, and trigger dynamic probes with 1-click presets.
- **Payload & Error Modals**: Formatted JSON inspection for captured method parameters, return values, and full stack traces.

---

## 📡 Dynamic Probe REST API

The agent exposes an embedded HTTP server on port `9876`.

### 1. Start Live Probe
```bash
curl -X POST http://localhost:9876/probe/start \
  -H "Content-Type: application/json" \
  -d '{
    "className": "com.mycompany.service.OrderService",
    "methodName": "processOrder",
    "maxCaptures": 5
  }'
```

### 2. Stop Probe
```bash
curl -X POST http://localhost:9876/probe/stop \
  -H "Content-Type: application/json" \
  -d '{
    "className": "com.mycompany.service.OrderService",
    "methodName": "processOrder"
  }'
```

### 3. Check Active Probes
```bash
curl -s http://localhost:9876/probe/status
```

### 4. Fetch Captured Payloads
```bash
curl -s http://localhost:9876/probe/events
```

Full API documentation: [docs/DYNAMIC_PROBE_API.md](docs/DYNAMIC_PROBE_API.md).

---

## ⚙️ Configuration Reference (`agent-config.json`)

```json
{
  "enabled": true,
  "jvmId": "order-srv-node1",
  "probePort": 9876,
  "probeEnabled": true,
  "logDirectory": "./agent-logs",
  "logFileName": "trace.log",
  "maxFileSizeBytes": 20971520,
  "maxBackupFiles": 5,
  "basePackages": [
    "com.mycompany.*"
  ],
  "excludePackages": [
    "com.mderyol.javaagent",
    "java.*",
    "javax.*",
    "org.springframework.*"
  ],
  "excludeMethodKeywords": [
    "toString",
    "hashCode",
    "equals",
    "<init>",
    "<clinit>"
  ],
  "queueCapacity": 20000,
  "batchSize": 500,
  "flushIntervalMs": 1000,
  "maxCallDepth": 64
}
```

Full configuration details: [docs/CONFIGURATION_GUIDE.md](docs/CONFIGURATION_GUIDE.md).

---

## 🧪 Performance & Benchmarks

| Metric | Baseline (No Agent) | Java Agent (Tracing On) | Java Agent + Active Probe |
| :--- | :--- | :--- | :--- |
| **Throughput (req/sec)** | 48,200 | 47,400 (< 1.7% impact) | 46,100 (< 4.4% impact) |
| **p95 Latency** | 2.1 ms | 2.2 ms | 2.5 ms |
| **p99 Latency** | 4.8 ms | 5.0 ms | 5.7 ms |
| **Memory Allocation** | Baseline | < 0.2 MB/s (recycled objects) | Isolated to captured payload |

---

## 📚 Documentation Index

- [Architecture & Internals](docs/ARCHITECTURE.md)
- [System & JVM Metrics Guide](docs/METRICS_GUIDE.md)
- [Spring Beans & Environment Guide](docs/SPRING_BEANS_AND_ENV_GUIDE.md)
- [Dynamic Probe API Reference](docs/DYNAMIC_PROBE_API.md)
- [Agent Configuration Guide](docs/CONFIGURATION_GUIDE.md)

---

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.