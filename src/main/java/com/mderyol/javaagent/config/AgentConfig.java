package com.mderyol.javaagent.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AgentConfig {
    private static volatile AgentConfig INSTANCE;

    private boolean enabled = true;

    // Target packages to trace. If empty or "*", all non-excluded classes are traced.
    private List<String> basePackages = new ArrayList<>();

    // Packages and third-party libraries excluded from instrumentation
    private List<String> excludePackages = new ArrayList<>(List.of(
            "com.mderyol.javaagent",
            "java.", "javax.", "jdk.", "sun.", "oracle.",
            "org.apache.", "org.springframework.", "org.hibernate.",
            "com.fasterxml.jackson", "net.bytebuddy"
    ));

    private List<String> excludeMethodKeywords = new ArrayList<>(List.of(
            "toString", "hashCode", "equals", "clone", "<init>", "<clinit>"
    ));

    private String logDirectory = "./agent-logs";
    private String logFileName = "trace.log";
    private long maxFileSizeBytes = 20 * 1024 * 1024L; // 20 MB
    private int maxBackupFiles = 5;

    // Asynchronous queue and memory protection
    private int queueCapacity = 20000;
    private int batchSize = 500;
    private long flushIntervalMs = 1000L;
    private int maxCallDepth = 64;

    // Dynamic Remote Probe & Viewer Port
    private boolean probeEnabled = true;

    @JsonAlias({"port", "serverPort"})
    private int probePort = 9876;
    private int maxPayloadLength = 2048;

    // JVM Instance Identifier (set via config or -Dagent.jvmId)
    @JsonAlias({"jvmId", "jvmInstanceId", "instanceId"})
    private String jvmInstanceId;

    // Multi-JVM conflict prevention: include jvmId in log file names
    private boolean includeJvmIdInFileName = true;

    // Periodic System & JVM Metrics Reporting
    private boolean metricsEnabled = true;
    private int metricsIntervalSeconds = 60;
    private String metricsFileName = "metrics.log";
    private long metricsMaxFileSizeBytes = 10 * 1024 * 1024L; // 10 MB
    private int metricsMaxBackupFiles = 3;

    // Environment Variables & System Properties Inspection
    private boolean logEnvironmentVariables = false;
    private boolean maskSensitiveEnv = true;
    private List<String> envFilter = new ArrayList<>(List.of("*"));

    // Spring Bean Inspection
    private boolean captureSpringBeans = true;
    private boolean logBeansOnStartup = false;

    public AgentConfig() {}

    public static AgentConfig getInstance() {
        if (INSTANCE == null) {
            synchronized (AgentConfig.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AgentConfig();
                }
            }
        }
        return INSTANCE;
    }

    public static AgentConfig init(String agentArgs) {
        String customConfigPath = extractConfigPathFromArgs(agentArgs);
        AgentConfig config = loadConfigFile(customConfigPath);

        if (agentArgs != null && !agentArgs.isBlank()) {
            config.parseArguments(agentArgs);
        }

        // Sistem özellikleri veya Ortam değişkenlerinden jvmId kontrolü (Config'te verilmediyse)
        if (config.jvmInstanceId == null || config.jvmInstanceId.isBlank()) {
            String sysJvm = System.getProperty("agent.jvmId", System.getProperty("jvmId"));
            if (sysJvm != null && !sysJvm.isBlank()) {
                config.jvmInstanceId = sysJvm.trim();
            } else {
                String envJvm = System.getenv("AGENT_JVM_ID");
                if (envJvm == null) envJvm = System.getenv("JVM_ID");
                if (envJvm != null && !envJvm.isBlank()) {
                    config.jvmInstanceId = envJvm.trim();
                }
            }
        }

        INSTANCE = config;
        return config;
    }

    private static String extractConfigPathFromArgs(String agentArgs) {
        if (agentArgs == null) return null;
        for (String pair : agentArgs.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && ("config".equalsIgnoreCase(kv[0].trim()) || "configFile".equalsIgnoreCase(kv[0].trim()))) {
                return kv[1].trim();
            }
        }
        return null;
    }

    private static AgentConfig loadConfigFile(String argConfigPath) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Priority 1: -javaagent:agent.jar=config=/path/to/my-config.json
        if (argConfigPath != null) {
            File f = new File(argConfigPath);
            if (f.exists()) {
                try (InputStream is = new FileInputStream(f)) {
                    System.out.println("[JavaAgent] Loading config from agentArgs path: " + f.getAbsolutePath());
                    return mapper.readValue(is, AgentConfig.class);
                } catch (Exception e) {
                    System.err.println("[JavaAgent] Failed to read config from " + argConfigPath + ": " + e.getMessage());
                }
            }
        }

        // Priority 2: -Dagent.config.path=/path/to/config.json
        String sysPath = System.getProperty("agent.config.path");
        if (sysPath != null) {
            File f = new File(sysPath);
            if (f.exists()) {
                try (InputStream is = new FileInputStream(f)) {
                    System.out.println("[JavaAgent] Loading config from system property: " + f.getAbsolutePath());
                    return mapper.readValue(is, AgentConfig.class);
                } catch (Exception e) {
                    System.err.println("[JavaAgent] Failed to read system property config: " + e.getMessage());
                }
            }
        }

        // Priority 3: Working directory config files ("agent-config.json" or "config-file.json")
        for (String localName : List.of("agent-config.json", "config-file.json")) {
            File localFile = new File(localName);
            if (localFile.exists()) {
                try (InputStream is = new FileInputStream(localFile)) {
                    System.out.println("[JavaAgent] Loading config from working directory: " + localFile.getAbsolutePath());
                    return mapper.readValue(is, AgentConfig.class);
                } catch (Exception e) {
                    System.err.println("[JavaAgent] Failed to read local config " + localName + ": " + e.getMessage());
                }
            }
        }

        // Priority 4: Classpath embedded config-file.json inside JAR
        try (InputStream is = AgentConfig.class.getClassLoader().getResourceAsStream("config-file.json")) {
            if (is != null) {
                System.out.println("[JavaAgent] Loading default config from classpath config-file.json");
                return mapper.readValue(is, AgentConfig.class);
            }
        } catch (Exception ignored) {}

        return new AgentConfig();
    }

    private void parseArguments(String agentArgs) {
        String[] pairs = agentArgs.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String val = kv[1].trim();
                switch (key) {
                    case "basePackages", "packages" -> {
                        if (val.isBlank() || val.equals("*")) {
                            this.basePackages = Collections.emptyList();
                        } else {
                            this.basePackages = Arrays.asList(val.split(";"));
                        }
                    }
                    case "excludePackages", "exclude" -> this.excludePackages = Arrays.asList(val.split(";"));
                    case "jvmId" -> this.jvmInstanceId = val;
                    case "port", "probePort" -> this.probePort = Integer.parseInt(val);
                    case "probeEnabled" -> this.probeEnabled = Boolean.parseBoolean(val);
                    case "logDir" -> this.logDirectory = val;
                    case "queueCapacity" -> this.queueCapacity = Integer.parseInt(val);
                    case "maxCallDepth" -> this.maxCallDepth = Integer.parseInt(val);
                    case "metricsEnabled" -> this.metricsEnabled = Boolean.parseBoolean(val);
                    case "metricsInterval", "metricsIntervalSeconds" -> this.metricsIntervalSeconds = Integer.parseInt(val);
                    case "logEnv", "logEnvironmentVariables" -> this.logEnvironmentVariables = Boolean.parseBoolean(val);
                    case "maskEnv", "maskSensitiveEnv" -> this.maskSensitiveEnv = Boolean.parseBoolean(val);
                    case "captureBeans", "captureSpringBeans" -> this.captureSpringBeans = Boolean.parseBoolean(val);
                    case "logBeans", "logBeansOnStartup" -> this.logBeansOnStartup = Boolean.parseBoolean(val);
                }
            }
        }
    }

    // Getters and Setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getBasePackages() { return basePackages; }
    public void setBasePackages(List<String> basePackages) { this.basePackages = basePackages; }
    public List<String> getExcludePackages() { return excludePackages; }
    public void setExcludePackages(List<String> excludePackages) { this.excludePackages = excludePackages; }
    public List<String> getExcludeMethodKeywords() { return excludeMethodKeywords; }
    public void setExcludeMethodKeywords(List<String> excludeMethodKeywords) { this.excludeMethodKeywords = excludeMethodKeywords; }
    public String getLogDirectory() { return logDirectory; }
    public void setLogDirectory(String logDirectory) { this.logDirectory = logDirectory; }
    public String getLogFileName() { return logFileName; }
    public void setLogFileName(String logFileName) { this.logFileName = logFileName; }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
    public int getMaxBackupFiles() { return maxBackupFiles; }
    public void setMaxBackupFiles(int maxBackupFiles) { this.maxBackupFiles = maxBackupFiles; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public long getFlushIntervalMs() { return flushIntervalMs; }
    public void setFlushIntervalMs(long flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; }
    public int getMaxCallDepth() { return maxCallDepth; }
    public void setMaxCallDepth(int maxCallDepth) { this.maxCallDepth = maxCallDepth; }
    public boolean isProbeEnabled() { return probeEnabled; }
    public void setProbeEnabled(boolean probeEnabled) { this.probeEnabled = probeEnabled; }
    public int getProbePort() { return probePort; }
    public void setProbePort(int probePort) { this.probePort = probePort; }
    public int getMaxPayloadLength() { return maxPayloadLength; }
    public void setMaxPayloadLength(int maxPayloadLength) { this.maxPayloadLength = maxPayloadLength; }
    public String getJvmInstanceId() { return jvmInstanceId; }
    public void setJvmInstanceId(String jvmInstanceId) { this.jvmInstanceId = jvmInstanceId; }
    public boolean isIncludeJvmIdInFileName() { return includeJvmIdInFileName; }
    public void setIncludeJvmIdInFileName(boolean includeJvmIdInFileName) { this.includeJvmIdInFileName = includeJvmIdInFileName; }

    public String getEffectiveJvmId() {
        if (jvmInstanceId != null && !jvmInstanceId.isBlank()) {
            return com.mderyol.javaagent.logger.HostInfo.sanitizeForFileName(jvmInstanceId.trim());
        }
        return com.mderyol.javaagent.logger.HostInfo.getSanitizedJvmId();
    }

    public String getEffectiveTraceFileName() {
        if (!includeJvmIdInFileName) {
            return (logFileName != null && !logFileName.isBlank()) ? logFileName : "trace.log";
        }
        String base = (logFileName != null && !logFileName.isBlank()) ? logFileName : "trace.log";
        if (base.contains("{jvmId}")) {
            return base.replace("{jvmId}", getEffectiveJvmId());
        }
        if (base.equals("trace.log")) {
            return "trace-" + getEffectiveJvmId() + ".log";
        }
        int dot = base.lastIndexOf('.');
        if (dot != -1) {
            return base.substring(0, dot) + "-" + getEffectiveJvmId() + base.substring(dot);
        }
        return base + "-" + getEffectiveJvmId();
    }

    public String getEffectiveProbeFileName() {
        if (!includeJvmIdInFileName) {
            return "probe-capture.log";
        }
        return "probe-capture-" + getEffectiveJvmId() + ".log";
    }

    public boolean isMetricsEnabled() { return metricsEnabled; }
    public void setMetricsEnabled(boolean metricsEnabled) { this.metricsEnabled = metricsEnabled; }
    public int getMetricsIntervalSeconds() { return metricsIntervalSeconds; }
    public void setMetricsIntervalSeconds(int metricsIntervalSeconds) { this.metricsIntervalSeconds = metricsIntervalSeconds; }
    public String getMetricsFileName() { return metricsFileName; }
    public void setMetricsFileName(String metricsFileName) { this.metricsFileName = metricsFileName; }
    public long getMetricsMaxFileSizeBytes() { return metricsMaxFileSizeBytes; }
    public void setMetricsMaxFileSizeBytes(long metricsMaxFileSizeBytes) { this.metricsMaxFileSizeBytes = metricsMaxFileSizeBytes; }
    public int getMetricsMaxBackupFiles() { return metricsMaxBackupFiles; }
    public void setMetricsMaxBackupFiles(int metricsMaxBackupFiles) { this.metricsMaxBackupFiles = metricsMaxBackupFiles; }

    public String getEffectiveMetricsFileName() {
        if (!includeJvmIdInFileName) {
            return (metricsFileName != null && !metricsFileName.isBlank()) ? metricsFileName : "metrics.log";
        }
        String base = (metricsFileName != null && !metricsFileName.isBlank()) ? metricsFileName : "metrics.log";
        if (base.contains("{jvmId}")) {
            return base.replace("{jvmId}", getEffectiveJvmId());
        }
        if (base.equals("metrics.log")) {
            return "metrics-" + getEffectiveJvmId() + ".log";
        }
        int dot = base.lastIndexOf('.');
        if (dot != -1) {
            return base.substring(0, dot) + "-" + getEffectiveJvmId() + base.substring(dot);
        }
        return base + "-" + getEffectiveJvmId();
    }

    public boolean isLogEnvironmentVariables() { return logEnvironmentVariables; }
    public void setLogEnvironmentVariables(boolean logEnvironmentVariables) { this.logEnvironmentVariables = logEnvironmentVariables; }
    public boolean isMaskSensitiveEnv() { return maskSensitiveEnv; }
    public void setMaskSensitiveEnv(boolean maskSensitiveEnv) { this.maskSensitiveEnv = maskSensitiveEnv; }
    public List<String> getEnvFilter() { return envFilter; }
    public void setEnvFilter(List<String> envFilter) { this.envFilter = envFilter; }

    public boolean isCaptureSpringBeans() { return captureSpringBeans; }
    public void setCaptureSpringBeans(boolean captureSpringBeans) { this.captureSpringBeans = captureSpringBeans; }
    public boolean isLogBeansOnStartup() { return logBeansOnStartup; }
    public void setLogBeansOnStartup(boolean logBeansOnStartup) { this.logBeansOnStartup = logBeansOnStartup; }
}
