package com.mderyol.javaagent.metrics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable snapshot of JVM and Host Machine performance and resource metrics.
 */
public class SystemMetricsSnapshot {

    private final String timestamp;
    private final long timestampEpochMs;
    private final String host;
    private final String jvmId;
    private final long pid;
    private final long uptimeSeconds;

    // JVM Performance
    private final long heapUsedBytes;
    private final long heapCommittedBytes;
    private final long heapMaxBytes;
    private final double heapUsagePercent;

    private final long nonHeapUsedBytes;
    private final long nonHeapCommittedBytes;

    private final int liveThreadCount;
    private final int peakThreadCount;
    private final int daemonThreadCount;

    private final long gcCollectionCount;
    private final long gcCollectionTimeMs;

    private final double processCpuLoadPercent;

    // Host Machine Performance
    private final double systemCpuLoadPercent;
    private final double systemLoadAverage;
    private final int availableProcessors;

    private final long totalPhysicalMemoryBytes;
    private final long freePhysicalMemoryBytes;
    private final long usedPhysicalMemoryBytes;
    private final double physicalMemoryUsagePercent;

    private final long totalDiskBytes;
    private final long usableDiskBytes;
    private final double diskUsagePercent;

    // Agent Internal Telemetry
    private final long loggedTraceEvents;
    private final long droppedTraceEvents;
    private final int queuePendingEvents;
    private final int activeProbesCount;

    public SystemMetricsSnapshot(Builder builder) {
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now().toString();
        this.timestampEpochMs = builder.timestampEpochMs;
        this.host = builder.host;
        this.jvmId = builder.jvmId;
        this.pid = builder.pid;
        this.uptimeSeconds = builder.uptimeSeconds;

        this.heapUsedBytes = builder.heapUsedBytes;
        this.heapCommittedBytes = builder.heapCommittedBytes;
        this.heapMaxBytes = builder.heapMaxBytes;
        this.heapUsagePercent = builder.heapUsagePercent;

        this.nonHeapUsedBytes = builder.nonHeapUsedBytes;
        this.nonHeapCommittedBytes = builder.nonHeapCommittedBytes;

        this.liveThreadCount = builder.liveThreadCount;
        this.peakThreadCount = builder.peakThreadCount;
        this.daemonThreadCount = builder.daemonThreadCount;

        this.gcCollectionCount = builder.gcCollectionCount;
        this.gcCollectionTimeMs = builder.gcCollectionTimeMs;

        this.processCpuLoadPercent = builder.processCpuLoadPercent;

        this.systemCpuLoadPercent = builder.systemCpuLoadPercent;
        this.systemLoadAverage = builder.systemLoadAverage;
        this.availableProcessors = builder.availableProcessors;

        this.totalPhysicalMemoryBytes = builder.totalPhysicalMemoryBytes;
        this.freePhysicalMemoryBytes = builder.freePhysicalMemoryBytes;
        this.usedPhysicalMemoryBytes = builder.usedPhysicalMemoryBytes;
        this.physicalMemoryUsagePercent = builder.physicalMemoryUsagePercent;

        this.totalDiskBytes = builder.totalDiskBytes;
        this.usableDiskBytes = builder.usableDiskBytes;
        this.diskUsagePercent = builder.diskUsagePercent;

        this.loggedTraceEvents = builder.loggedTraceEvents;
        this.droppedTraceEvents = builder.droppedTraceEvents;
        this.queuePendingEvents = builder.queuePendingEvents;
        this.activeProbesCount = builder.activeProbesCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", timestamp);
        map.put("timestampEpochMs", timestampEpochMs);
        map.put("host", host);
        map.put("jvmId", jvmId);
        map.put("pid", pid);
        map.put("uptimeSeconds", uptimeSeconds);

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedBytes", heapUsedBytes);
        jvm.put("heapCommittedBytes", heapCommittedBytes);
        jvm.put("heapMaxBytes", heapMaxBytes);
        jvm.put("heapUsagePercent", round(heapUsagePercent));
        jvm.put("nonHeapUsedBytes", nonHeapUsedBytes);
        jvm.put("nonHeapCommittedBytes", nonHeapCommittedBytes);
        jvm.put("liveThreadCount", liveThreadCount);
        jvm.put("peakThreadCount", peakThreadCount);
        jvm.put("daemonThreadCount", daemonThreadCount);
        jvm.put("gcCollectionCount", gcCollectionCount);
        jvm.put("gcCollectionTimeMs", gcCollectionTimeMs);
        jvm.put("processCpuLoadPercent", round(processCpuLoadPercent));
        map.put("jvm", jvm);

        Map<String, Object> system = new LinkedHashMap<>();
        system.put("systemCpuLoadPercent", round(systemCpuLoadPercent));
        system.put("systemLoadAverage", round(systemLoadAverage));
        system.put("availableProcessors", availableProcessors);
        system.put("totalPhysicalMemoryBytes", totalPhysicalMemoryBytes);
        system.put("freePhysicalMemoryBytes", freePhysicalMemoryBytes);
        system.put("usedPhysicalMemoryBytes", usedPhysicalMemoryBytes);
        system.put("physicalMemoryUsagePercent", round(physicalMemoryUsagePercent));
        system.put("totalDiskBytes", totalDiskBytes);
        system.put("usableDiskBytes", usableDiskBytes);
        system.put("diskUsagePercent", round(diskUsagePercent));
        map.put("system", system);

        Map<String, Object> agent = new LinkedHashMap<>();
        agent.put("loggedTraceEvents", loggedTraceEvents);
        agent.put("droppedTraceEvents", droppedTraceEvents);
        agent.put("queuePendingEvents", queuePendingEvents);
        agent.put("activeProbesCount", activeProbesCount);
        map.put("agent", agent);

        return map;
    }

    private static double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    // Getters
    public String getTimestamp() { return timestamp; }
    public long getTimestampEpochMs() { return timestampEpochMs; }
    public String getHost() { return host; }
    public String getJvmId() { return jvmId; }
    public long getPid() { return pid; }
    public long getUptimeSeconds() { return uptimeSeconds; }
    public long getHeapUsedBytes() { return heapUsedBytes; }
    public long getHeapCommittedBytes() { return heapCommittedBytes; }
    public long getHeapMaxBytes() { return heapMaxBytes; }
    public double getHeapUsagePercent() { return heapUsagePercent; }
    public long getNonHeapUsedBytes() { return nonHeapUsedBytes; }
    public long getNonHeapCommittedBytes() { return nonHeapCommittedBytes; }
    public int getLiveThreadCount() { return liveThreadCount; }
    public int getPeakThreadCount() { return peakThreadCount; }
    public int getDaemonThreadCount() { return daemonThreadCount; }
    public long getGcCollectionCount() { return gcCollectionCount; }
    public long getGcCollectionTimeMs() { return gcCollectionTimeMs; }
    public double getProcessCpuLoadPercent() { return processCpuLoadPercent; }
    public double getSystemCpuLoadPercent() { return systemCpuLoadPercent; }
    public double getSystemLoadAverage() { return systemLoadAverage; }
    public int getAvailableProcessors() { return availableProcessors; }
    public long getTotalPhysicalMemoryBytes() { return totalPhysicalMemoryBytes; }
    public long getFreePhysicalMemoryBytes() { return freePhysicalMemoryBytes; }
    public long getUsedPhysicalMemoryBytes() { return usedPhysicalMemoryBytes; }
    public double getPhysicalMemoryUsagePercent() { return physicalMemoryUsagePercent; }
    public long getTotalDiskBytes() { return totalDiskBytes; }
    public long getUsableDiskBytes() { return usableDiskBytes; }
    public double getDiskUsagePercent() { return diskUsagePercent; }
    public long getLoggedTraceEvents() { return loggedTraceEvents; }
    public long getDroppedTraceEvents() { return droppedTraceEvents; }
    public int getQueuePendingEvents() { return queuePendingEvents; }
    public int getActiveProbesCount() { return activeProbesCount; }

    public static class Builder {
        private String timestamp;
        private long timestampEpochMs;
        private String host;
        private String jvmId;
        private long pid;
        private long uptimeSeconds;

        private long heapUsedBytes;
        private long heapCommittedBytes;
        private long heapMaxBytes;
        private double heapUsagePercent;

        private long nonHeapUsedBytes;
        private long nonHeapCommittedBytes;

        private int liveThreadCount;
        private int peakThreadCount;
        private int daemonThreadCount;

        private long gcCollectionCount;
        private long gcCollectionTimeMs;

        private double processCpuLoadPercent;

        private double systemCpuLoadPercent;
        private double systemLoadAverage;
        private int availableProcessors;

        private long totalPhysicalMemoryBytes;
        private long freePhysicalMemoryBytes;
        private long usedPhysicalMemoryBytes;
        private double physicalMemoryUsagePercent;

        private long totalDiskBytes;
        private long usableDiskBytes;
        private double diskUsagePercent;

        private long loggedTraceEvents;
        private long droppedTraceEvents;
        private int queuePendingEvents;
        private int activeProbesCount;

        public Builder timestamp(String val) { this.timestamp = val; return this; }
        public Builder timestampEpochMs(long val) { this.timestampEpochMs = val; return this; }
        public Builder host(String val) { this.host = val; return this; }
        public Builder jvmId(String val) { this.jvmId = val; return this; }
        public Builder pid(long val) { this.pid = val; return this; }
        public Builder uptimeSeconds(long val) { this.uptimeSeconds = val; return this; }
        public Builder heapUsedBytes(long val) { this.heapUsedBytes = val; return this; }
        public Builder heapCommittedBytes(long val) { this.heapCommittedBytes = val; return this; }
        public Builder heapMaxBytes(long val) { this.heapMaxBytes = val; return this; }
        public Builder heapUsagePercent(double val) { this.heapUsagePercent = val; return this; }
        public Builder nonHeapUsedBytes(long val) { this.nonHeapUsedBytes = val; return this; }
        public Builder nonHeapCommittedBytes(long val) { this.nonHeapCommittedBytes = val; return this; }
        public Builder liveThreadCount(int val) { this.liveThreadCount = val; return this; }
        public Builder peakThreadCount(int val) { this.peakThreadCount = val; return this; }
        public Builder daemonThreadCount(int val) { this.daemonThreadCount = val; return this; }
        public Builder gcCollectionCount(long val) { this.gcCollectionCount = val; return this; }
        public Builder gcCollectionTimeMs(long val) { this.gcCollectionTimeMs = val; return this; }
        public Builder processCpuLoadPercent(double val) { this.processCpuLoadPercent = val; return this; }
        public Builder systemCpuLoadPercent(double val) { this.systemCpuLoadPercent = val; return this; }
        public Builder systemLoadAverage(double val) { this.systemLoadAverage = val; return this; }
        public Builder availableProcessors(int val) { this.availableProcessors = val; return this; }
        public Builder totalPhysicalMemoryBytes(long val) { this.totalPhysicalMemoryBytes = val; return this; }
        public Builder freePhysicalMemoryBytes(long val) { this.freePhysicalMemoryBytes = val; return this; }
        public Builder usedPhysicalMemoryBytes(long val) { this.usedPhysicalMemoryBytes = val; return this; }
        public Builder physicalMemoryUsagePercent(double val) { this.physicalMemoryUsagePercent = val; return this; }
        public Builder totalDiskBytes(long val) { this.totalDiskBytes = val; return this; }
        public Builder usableDiskBytes(long val) { this.usableDiskBytes = val; return this; }
        public Builder diskUsagePercent(double val) { this.diskUsagePercent = val; return this; }
        public Builder loggedTraceEvents(long val) { this.loggedTraceEvents = val; return this; }
        public Builder droppedTraceEvents(long val) { this.droppedTraceEvents = val; return this; }
        public Builder queuePendingEvents(int val) { this.queuePendingEvents = val; return this; }
        public Builder activeProbesCount(int val) { this.activeProbesCount = val; return this; }

        public SystemMetricsSnapshot build() {
            return new SystemMetricsSnapshot(this);
        }
    }
}
