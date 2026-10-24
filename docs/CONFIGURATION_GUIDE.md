# Agent Configuration Guide

The Java Agent supports dynamic configuration via JSON files, agent command-line arguments, and JVM system properties.

---

## Configuration Precedence

Settings are resolved in the following priority order (highest to lowest):
1. **JVM System Properties** (e.g. `-Dagent.probePort=9999`)
2. **Java Agent String Arguments** (e.g. `-javaagent:javaagent-2.0.0.jar=config=/etc/agent.json,probePort=9999`)
3. **External Configuration File** (`agent-config.json` in the current working directory or path specified by `config=...`)
4. **Classpath Fallback** (`config-file.json` embedded in the JAR)
5. **Built-in Hardcoded Defaults**

---

## Configuration Schema (`agent-config.json`)

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
    "jdk.*",
    "sun.*",
    "oracle.*",
    "org.springframework.*",
    "org.apache.*",
    "org.hibernate.*",
    "com.fasterxml.jackson.*",
    "net.bytebuddy.*",
    "io.netty.*",
    "com.mysql.*",
    "org.postgresql.*"
  ],
  "excludeMethodKeywords": [
    "toString",
    "hashCode",
    "equals",
    "clone",
    "<init>",
    "<clinit>"
  ],
  "queueCapacity": 20000,
  "batchSize": 500,
  "flushIntervalMs": 1000,
  "maxCallDepth": 64,
  "maxPayloadLength": 2048,
  "metricsEnabled": true,
  "metricsIntervalSeconds": 60,
  "metricsFileName": "metrics.log",
  "metricsMaxFileSizeBytes": 10485760,
  "metricsMaxBackupFiles": 3,
  "logEnvironmentVariables": false,
  "maskSensitiveEnv": true,
  "captureSpringBeans": true,
  "logBeansOnStartup": false
}
```

---

## Parameter Reference

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `enabled` | `boolean` | `true` | Master switch. When `false`, byte-buddy transformation is completely skipped. |
| `jvmId` | `String` | *(Process PID)* | Custom identifier for the JVM node (e.g., `payment-node-3`). Appended to log file names to avoid cross-JVM conflicts. |
| `probePort` | `int` | `9876` | Port for the embedded HTTP management server and web dashboard. |
| `probeEnabled` | `boolean` | `true` | Enables or disables the embedded HTTP management server. |
| `logDirectory` | `String` | `./agent-logs` | Target folder for structured trace output, metrics, and probe files. |
| `logFileName` | `String` | `trace.log` | Base file name for telemetry execution traces. |
| `maxFileSizeBytes` | `long` | `20971520` (20MB) | File size threshold at which `trace.log` is rotated. |
| `maxBackupFiles` | `int` | `5` | Retention count for archived rotated log files (`trace.log.1`, `trace.log.2`, etc.). |
| `basePackages` | `List<String>` | `["*"]` | Target package patterns to instrument. Use wildcards like `com.company.services.*`. |
| `excludePackages` | `List<String>` | *(Common frameworks)* | Package prefixes to strictly bypass from class transformation. |
| `excludeMethodKeywords`| `List<String>` | `["toString", ...]` | Method names or substrings to bypass (e.g. constructors `<init>`, `equals`). |
| `queueCapacity` | `int` | `20000` | Capacity of the non-blocking in-memory ring-buffer (`ArrayBlockingQueue`). |
| `batchSize` | `int` | `500` | Maximum number of events flushed to disk per disk write cycle. |
| `flushIntervalMs` | `long` | `1000` | Periodic timer interval (in ms) to force flush queued telemetry to disk. |
| `maxCallDepth` | `int` | `64` | Maximum call tree recursion depth tracked in context. |
| `maxPayloadLength` | `int` | `2048` | String length truncation limit for captured parameter and return value payloads. |
| `metricsEnabled` | `boolean` | `true` | Enables periodic Host Machine & JVM resource telemetry (CPU, RAM, Threads, GC, Disk). |
| `metricsIntervalSeconds`| `int` | `60` | Metrics sampling period in seconds (minimum 5s). |
| `metricsFileName` | `String` | `"metrics.log"` | Base filename for the rotating NDJSON metrics log. |
| `metricsMaxFileSizeBytes`| `long` | `10485760` (10MB) | File size rotation threshold for the metrics log. |
| `metricsMaxBackupFiles`| `int` | `3` | Retention count for archived metrics files. |
| `logEnvironmentVariables`| `boolean`| `false` | When true, writes environment variables and system properties to `environment-<jvmId>.log` on startup. |
| `maskSensitiveEnv` | `boolean` | `true` | Automatically masks sensitive tokens (`password`, `secret`, `token`, `key`, `auth`, `jwt`). |
| `captureSpringBeans` | `boolean` | `true` | Detects Spring Boot `ApplicationContext` via non-invasive reflection when present. |
| `logBeansOnStartup` | `boolean` | `false` | When true, writes registered Spring beans to `beans-<jvmId>.log` when context refreshes. |

---

## JVM Command-Line Overrides

You can pass configuration options directly when launching the application:

```bash
java -javaagent:target/javaagent-2.0.0.jar=config=/etc/agent.json,jvmId=billing-app-1,probePort=9090 \
     -jar your-application.jar
```
