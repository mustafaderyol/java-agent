package com.mderyol.javaagent.env;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mderyol.javaagent.config.AgentConfig;
import com.mderyol.javaagent.logger.HostInfo;

/**
 * Manages environment variables and system properties inspection,
 * with automatic sensitive data masking and optional file logging.
 */
public class EnvironmentManager {

    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "password", "passwd", "pwd", "secret", "token", "apikey", "api_key",
            "private", "auth", "credential", "jwt", "access_key", "certificate"
    );

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Retrieves environment variables with optional sensitive masking.
     */
    public static Map<String, String> getEnvironmentVariables(boolean maskSensitive, List<String> filters) {
        Map<String, String> rawEnv = System.getenv();
        Map<String, String> filtered = new TreeMap<>();

        for (Map.Entry<String, String> entry : rawEnv.entrySet()) {
            String key = entry.getKey();
            if (matchesFilter(key, filters)) {
                String val = entry.getValue();
                if (maskSensitive && isSensitive(key)) {
                    val = maskValue(val);
                }
                filtered.put(key, val);
            }
        }
        return filtered;
    }

    /**
     * Retrieves JVM system properties with optional sensitive masking.
     */
    public static Map<String, String> getSystemProperties(boolean maskSensitive) {
        Properties props = System.getProperties();
        Map<String, String> filtered = new TreeMap<>();

        for (String key : props.stringPropertyNames()) {
            String val = props.getProperty(key);
            if (maskSensitive && isSensitive(key)) {
                val = maskValue(val);
            }
            filtered.put(key, val);
        }
        return filtered;
    }

    public static Map<String, Object> getCombinedSnapshot(boolean maskSensitive, List<String> filters) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("timestamp", Instant.now().toString());
        snapshot.put("host", HostInfo.getHostName());
        snapshot.put("jvmId", HostInfo.getJvmId());
        snapshot.put("pid", HostInfo.getPid());
        snapshot.put("environmentCount", System.getenv().size());
        snapshot.put("systemPropertiesCount", System.getProperties().size());
        snapshot.put("masked", maskSensitive);
        snapshot.put("environmentVariables", getEnvironmentVariables(maskSensitive, filters));
        snapshot.put("systemProperties", getSystemProperties(maskSensitive));
        return snapshot;
    }

    /**
     * Writes environment variables to agent-logs/environment-<jvmId>.log if configured.
     */
    public static void logToFileIfEnabled(AgentConfig config) {
        if (!config.isLogEnvironmentVariables()) return;

        try {
            Path logDir = Paths.get(config.getLogDirectory());
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }

            String fileName = "environment-" + config.getEffectiveJvmId() + ".log";
            Path targetFile = logDir.resolve(fileName);

            Map<String, Object> snapshot = getCombinedSnapshot(config.isMaskSensitiveEnv(), config.getEnvFilter());
            String json = MAPPER.writeValueAsString(snapshot);

            try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write(json);
            }
            System.out.println("[JavaAgent] Environment variables logged to: " + targetFile.toAbsolutePath());
        } catch (Throwable t) {
            System.err.println("[JavaAgent] Failed to log environment variables: " + t.getMessage());
        }
    }

    public static boolean isSensitive(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase(Locale.ROOT);
        for (String kw : SENSITIVE_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    public static String maskValue(String val) {
        if (val == null || val.isBlank()) return "******";
        if (val.length() <= 4) return "******";
        // Preserve first character, mask the rest
        return val.charAt(0) + "******" + val.charAt(val.length() - 1);
    }

    private static boolean matchesFilter(String key, List<String> filters) {
        if (filters == null || filters.isEmpty()) return true;
        for (String filter : filters) {
            if (filter == null || filter.isBlank() || "*".equals(filter.trim())) return true;
            String f = filter.trim();
            if (f.endsWith("*")) {
                String prefix = f.substring(0, f.length() - 1);
                if (key.startsWith(prefix)) return true;
            } else if (key.equalsIgnoreCase(f)) {
                return true;
            }
        }
        return false;
    }
}
