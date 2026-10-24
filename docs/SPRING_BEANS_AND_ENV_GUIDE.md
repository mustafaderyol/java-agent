# Spring Beans & Environment Variables Guide

The Java Agent includes on-demand inspection and logging for **Environment Variables & System Properties** and **Registered Spring Beans**, designed for zero performance overhead and enterprise-grade secret masking.

---

## 1. Environment Variables & System Properties Inspection

### Security & Automatic Masking
Environment variables and JVM system properties often contain sensitive tokens, credentials, and API keys. The agent incorporates an automatic masking engine that sanitizes any key containing sensitive patterns (e.g. `password`, `secret`, `token`, `key`, `credential`, `private`, `jwt`, `auth`).

- Example: `DATABASE_PASSWORD` -> `p******d` (or `******` if short).
- Masking is enabled by default (`maskSensitiveEnv: true`).

### File Logging (`agent-logs/environment-<jvmId>.log`)
To avoid disk clutter and security risks, environment variable logging to disk is **disabled by default** (`logEnvironmentVariables: false`).
When enabled:
```json
{
  "logEnvironmentVariables": true,
  "maskSensitiveEnv": true
}
```
The agent automatically writes the complete snapshot of environment variables and JVM system properties to:
`agent-logs/environment-<jvmId>.log` (formatted as indented JSON).

### REST API Endpoint (`GET /env`)
Inspect environment variables and system properties via HTTP:
```bash
# Query masked environment variables and system properties
curl -s http://localhost:9876/env | jq .

# Query with package or prefix filter
curl -s "http://localhost:9876/env?filter=JAVA_*,SPRING_*" | jq .

# Explicitly unmask values (if authorized)
curl -s "http://localhost:9876/env?unmask=true" | jq .
```

---

## 2. Spring Framework Registered Beans Discovery

### Zero-Dependency Non-Invasive Interception
The Java Agent has **zero compile-time dependency on Spring Framework**.
When running inside a Spring Boot application:
1. The agent intercepts `AbstractApplicationContext.finishRefresh()`.
2. It safely captures a reference to the active `ApplicationContext`.
3. It exposes bean metadata (Bean Name, Type, Package, Scope, Aliases, and Public Methods) via pure reflection.

### File Logging (`agent-logs/beans-<jvmId>.log`)
When `logBeansOnStartup: true` is configured:
Once Spring Boot completes its startup lifecycle, the agent writes the complete bean registry to:
`agent-logs/beans-<jvmId>.log`

### REST API Endpoint (`GET /beans`)
```bash
# List all registered Spring beans
curl -s http://localhost:9876/beans | jq .

# Filter by bean name (case-insensitive substring)
curl -s "http://localhost:9876/beans?name=order" | jq .

# Filter by package
curl -s "http://localhost:9876/beans?package=com.mycompany.service" | jq .
```

#### Example Output:
```json
{
  "status": "SUCCESS",
  "totalBeans": 184,
  "filteredBeansCount": 1,
  "beans": [
    {
      "beanName": "orderService",
      "className": "com.mycompany.service.OrderService",
      "packageName": "com.mycompany.service",
      "isSingleton": true,
      "publicMethods": [
        "processOrder",
        "calculateDiscount",
        "cancelOrder"
      ]
    }
  ]
}
```

---

## 3. Interactive Web Dashboard Integration

Open `http://localhost:9876/ui` in your browser:
- **`🌱 Beans` Button**: Opens the Spring Beans Explorer modal. Search any bean, view its class, type, singleton status, and click **"🎯 Probe"** on any public method to instantly attach live parameter and response capture probes without typing class names manually!
- **`🌐 Env` Button**: Opens the Environment & System Properties modal. Filter variables, toggle live masking on/off, and copy the full JSON payload with 1 click.

---

## ⚙️ Configuration Reference (`agent-config.json`)

```json
{
  "logEnvironmentVariables": false,
  "maskSensitiveEnv": true,
  "captureSpringBeans": true,
  "logBeansOnStartup": false
}
```

### Command-Line Arguments
```bash
java -javaagent:target/javaagent-2.0.0.jar=logEnvironmentVariables=true,logBeansOnStartup=true \
     -jar your-app.jar
```
