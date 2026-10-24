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

### 4. `GET /probe/events`
Returns in-memory captured events (up to the circular buffer limit of the last 100 events).

#### Example cURL
```bash
curl -s http://localhost:9876/probe/events
```

#### Response (200 OK)
```json
[
  {
    "traceId": "9f7b198c-ec6f-4318-ae71-29e2f693e5fa",
    "spanId": "c85303df-7935-42d4-a8fe-3bc4547908b8",
    "timestamp": 1729804215120,
    "className": "com.mderyol.testapp.service.OrderService",
    "methodName": "processOrder",
    "requestPayload": "[Order{orderId='ORD-1001', amount=149.99}]",
    "responsePayload": "OrderResponse{status='SUCCESS', orderId='ORD-1001'}",
    "durationMs": 42
  }
]
```

---

### 5. `GET /ui`
Serves the embedded, interactive single-page Trace & Probe Management Dashboard.
Point your browser to:
```
http://localhost:9876/ui
```
*(Also accessible via `/`)*
