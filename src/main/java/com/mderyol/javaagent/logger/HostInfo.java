package com.mderyol.javaagent.logger;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;

public class HostInfo {
    private static final String HOST_NAME;
    private static final long PID;
    private static volatile String JVM_ID;

    static {
        String host = "unknown-host";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            String envHost = System.getenv("HOSTNAME");
            if (envHost != null) {
                host = envHost;
            }
        }
        HOST_NAME = host;

        long pid = -1;
        try {
            pid = ProcessHandle.current().pid();
        } catch (Throwable t) {
            try {
                String jvmName = ManagementFactory.getRuntimeMXBean().getName();
                pid = Long.parseLong(jvmName.split("@")[0]);
            } catch (Exception ignored) {}
        }
        PID = pid;
        JVM_ID = HOST_NAME + "-jvm-" + PID;
    }

    public static String getHostName() {
        return HOST_NAME;
    }

    public static long getPid() {
        return PID;
    }

    public static String getJvmId() {
        return JVM_ID;
    }

    public static void setCustomJvmId(String customId) {
        if (customId != null && !customId.isBlank()) {
            JVM_ID = customId;
        }
    }

    public static String getSanitizedJvmId() {
        return sanitizeForFileName(JVM_ID);
    }

    public static String sanitizeForFileName(String input) {
        if (input == null || input.isBlank()) return "jvm";
        return input.replace(".local", "")
                .replaceAll("[^a-zA-Z0-9._-]", "-")
                .replaceAll("-+", "-");
    }
}
