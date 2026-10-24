# Dynamic Probe API Reference

The Java Agent features an embedded, non-blocking HTTP management server (default port `9876`) that allows DevOps and SRE teams to dynamically attach live method-level parameter (Request) and return value (Response) capture probes at runtime **without restarting or redeploying the JVM**.

---

## Overview

In high-throughput microservices, serializing every method argument and return value at all times causes heavy CPU and I/O overhead. The Java Agent solves this by remaining **zero-overhead** during normal operations (recording only timing, class/method names, and call hierarchies) and enabling **dynamic, targeted inspection** on-demand.

### Key Capabilities
- **Zero-Restart Instrumentation**: Enable or disable parameter/response capture on any method while the JVM is serving live production traffic.
- **Downstream Tree Inheritance**: When a probe is attached to an entry point (e.g. a REST Controller or SOAP Endpoint), all downstream child services, repositories, and helper methods executed within that call hierarchy automatically inherit the capture flag.
- **Auto-Expiry Limits**: Protect against unbounded memory growth or runaway logging by setting `maxCaptures` (e.g. capture only 5 invocations and automatically deactivate).
- **Embedded Web Dashboard**: Accessible directly at `http://<host>:9876/ui`.

---

## Endpoints

### 1. `POST /probe/start`
Activates dynamic capture on a specific target class and method.

#### Request Body (JSON)
```json
{
  "className": "com.mderyol.testapp.service.OrderService",
  "methodName": "processOrder",
  "maxCaptures": 10
}
```

| Field | Type | Required | Description | Default |
| :--- | :--- | :--- | :--- | :--- |
| `className` | `String` | Yes | Fully-qualified class name. | - |
| `methodName` | `String` | Yes | Target method name to probe. | - |
| `maxCaptures` | `Integer` | No | Number of invocations to capture before auto-stopping. | `10` |

#### Example cURL
```bash
curl -X POST http://localhost:9876/probe/start \
  -H "Content-Type: application/json" \
  -d '{
    "className": "com.mderyol.testapp.service.OrderService",
    "methodName": "processOrder",
    "maxCaptures": 5
  }'
```

#### Response (200 OK)
```json
{
  "status": "PROBE_ACTIVATED",
  "className": "com.mderyol.testapp.service.OrderService",
  "methodName": "processOrder",
  "maxCaptures": 5
}
```

---

### 2. `POST /probe/stop`
Deactivates active probe capture for a specific target class and method.

#### Request Body (JSON)
```json
{
  "className": "com.mderyol.testapp.service.OrderService",
  "methodName": "processOrder"
}
```

#### Example cURL
```bash
curl -X POST http://localhost:9876/probe/stop \
  -H "Content-Type: application/json" \
  -d '{
    "className": "com.mderyol.testapp.service.OrderService",
    "methodName": "processOrder"
  }'
```

#### Response (200 OK)
```json
{
  "status": "PROBE_DEACTIVATED",
  "className": "com.mderyol.testapp.service.OrderService",
  "methodName": "processOrder"
}
```

---

### 3. `GET /probe/status`
Returns the status of all active probes, current capture counts, and max limits.

#### Example cURL
```bash
curl -s http://localhost:9876/probe/status
```

#### Response (200 OK)
```json
{
  "activeProbesCount": 1,
  "activeProbes": [
    {
      "key": "com.mderyol.testapp.service.OrderService#processOrder",
      "className": "com.mderyol.testapp.service.OrderService",
      "methodName": "processOrder",
      "capturedCount": 3,
      "maxCaptures": 5,
      "active": true
    }
  ]
}
```

---

---

### 4. `GET /metrics`
Returns an instant JSON snapshot of JVM and Host OS resources (CPU, Memory, Threads, GC, Disk).

#### Example cURL
```bash
curl -s http://localhost:9876/metrics | jq .
```

---

### 5. `GET /env`
Inspects environment variables and JVM system properties with automatic secret masking.

#### Example cURL
```bash
# Masked by default
curl -s http://localhost:9876/env | jq .

# Filter by prefix
curl -s "http://localhost:9876/env?filter=JAVA_*,SPRING_*" | jq .

# Unmasked (if authorized)
curl -s "http://localhost:9876/env?unmask=true" | jq .
```

---

### 6. `GET /beans`
Returns registered Spring framework beans discovered via non-invasive reflection.

#### Example cURL
```bash
# List all beans
curl -s http://localhost:9876/beans | jq .

# Filter by bean name
curl -s "http://localhost:9876/beans?name=order" | jq .

# Filter by package
curl -s "http://localhost:9876/beans?package=com.mycompany.service" | jq .
```

---

### 7. `GET /health`
Returns agent operational health for Kubernetes liveness/readiness probes.

#### Example cURL
```bash
curl -s http://localhost:9876/health | jq .
```

---

### 8. `GET /ui`
Serves the embedded, interactive single-page Trace, Probe & Resource Dashboard.
Point your browser to:
```
http://localhost:9876/ui
```
*(Also accessible via `/`)*

---

### 9. `GET /error-log/status`
Returns the operational status of the dedicated method exception stack trace logger and count of captured errors.

#### Example cURL
```bash
curl -s http://localhost:9876/error-log/status | jq .
```

#### Response (200 OK)
```json
{
  "status": "UP",
  "host": "node-1.corp",
  "jvmId": "order-srv-node1",
  "pid": 24605,
  "errorLoggingEnabled": true,
  "outputFile": "/app/agent-logs/error-order-srv-node1.log",
  "loggedErrorCount": 4
}
```

---

### 10. `POST /error-log/toggle`
Dynamically enables or disables method error stack trace logging at runtime without restarting the JVM.

#### Example cURL: Toggle State
```bash
# Toggle current state (Enabled -> Disabled or Disabled -> Enabled)
curl -s -X POST http://localhost:9876/error-log/toggle | jq .
```

#### Example cURL: Explicitly Enable or Disable
```bash
# Disable error stack trace logging
curl -s -X POST http://localhost:9876/error-log/toggle \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}' | jq .

# Enable error stack trace logging
curl -s -X POST http://localhost:9876/error-log/toggle \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}' | jq .
```

#### Response (200 OK)
```json
{
  "status": "SUCCESS",
  "message": "Method error stack trace logging is now DISABLED",
  "errorLoggingEnabled": false,
  "outputFile": "/app/agent-logs/error-order-srv-node1.log",
  "loggedErrorCount": 4
}
```


