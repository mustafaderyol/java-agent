package com.mderyol.javaagent.metrics;

import java.io.File;
import java.lang.management.*;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import com.mderyol.javaagent.config.AgentConfig;
import com.mderyol.javaagent.logger.AsyncTraceLogger;
import com.mderyol.javaagent.logger.HostInfo;
import com.mderyol.javaagent.probe.DynamicProbeManager;

/**
 * High-performance, non-blocking metrics collector that samples JVM and Host OS resources.
 */
public class MetricsCollector {

    private static final OperatingSystemMXBean OS_BEAN = ManagementFactory.getOperatingSystemMXBean();
    private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
    private static final ThreadMXBean THREAD_BEAN = ManagementFactory.getThreadMXBean();
    private static final RuntimeMXBean RUNTIME_BEAN = ManagementFactory.getRuntimeMXBean();
    private static final List<GarbageCollectorMXBean> GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();

    // Reflection handles for com.sun.management.OperatingSystemMXBean methods for maximum cross-JVM safety
    private static Method processCpuLoadMethod;
    private static Method systemCpuLoadMethod;
    private static Method totalMemoryMethod;
    private static Method freeMemoryMethod;

    static {
        try {
            Class<?> sunOsClass = Class.forName("com.sun.management.OperatingSystemMXBean");
            if (sunOsClass.isInstance(OS_BEAN)) {
                try {
                    processCpuLoadMethod = sunOsClass.getMethod("getProcessCpuLoad");
                } catch (Exception ignored) {}

                try {
                    systemCpuLoadMethod = sunOsClass.getMethod("getCpuLoad");
                } catch (Exception e) {
                    try {
                        systemCpuLoadMethod = sunOsClass.getMethod("getSystemCpuLoad");
                    } catch (Exception ignored) {}
                }

                try {
                    totalMemoryMethod = sunOsClass.getMethod("getTotalMemorySize");
                } catch (Exception e) {
                    try {
                        totalMemoryMethod = sunOsClass.getMethod("getTotalPhysicalMemorySize");
                    } catch (Exception ignored) {}
                }

                try {
                    freeMemoryMethod = sunOsClass.getMethod("getFreeMemorySize");
                } catch (Exception e) {
                    try {
                        freeMemoryMethod = sunOsClass.getMethod("getFreePhysicalMemorySize");
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable ignored) {
            // Non-sun JVM or restricted security manager
        }
    }

    public static SystemMetricsSnapshot collect(AgentConfig config) {
        long now = System.currentTimeMillis();
        SystemMetricsSnapshot.Builder builder = SystemMetricsSnapshot.builder()
                .timestamp(Instant.ofEpochMilli(now).toString())
                .timestampEpochMs(now)
                .host(HostInfo.getHostName())
                .jvmId(config.getEffectiveJvmId())
                .pid(HostInfo.getPid())
                .uptimeSeconds(RUNTIME_BEAN.getUptime() / 1000);

        // 1. JVM Memory Metrics
        try {
            MemoryUsage heap = MEMORY_BEAN.getHeapMemoryUsage();
            long heapUsed = heap.getUsed();
            long heapCommitted = heap.getCommitted();
            long heapMax = heap.getMax() > 0 ? heap.getMax() : heapCommitted;
            double heapUsagePct = heapMax > 0 ? ((double) heapUsed / heapMax) * 100.0 : 0.0;

            builder.heapUsedBytes(heapUsed)
                    .heapCommittedBytes(heapCommitted)
                    .heapMaxBytes(heapMax)
                    .heapUsagePercent(heapUsagePct);

            MemoryUsage nonHeap = MEMORY_BEAN.getNonHeapMemoryUsage();
            builder.nonHeapUsedBytes(nonHeap.getUsed())
                    .nonHeapCommittedBytes(nonHeap.getCommitted());
        } catch (Throwable ignored) {}

        // 2. JVM Thread Metrics
        try {
            builder.liveThreadCount(THREAD_BEAN.getThreadCount())
                    .peakThreadCount(THREAD_BEAN.getPeakThreadCount())
                    .daemonThreadCount(THREAD_BEAN.getDaemonThreadCount());
        } catch (Throwable ignored) {}

        // 3. Garbage Collection Metrics
        try {
            long totalGcCount = 0;
            long totalGcTime = 0;
            for (GarbageCollectorMXBean gc : GC_BEANS) {
                long c = gc.getCollectionCount();
                if (c > 0) totalGcCount += c;
                long t = gc.getCollectionTime();
                if (t > 0) totalGcTime += t;
            }
            builder.gcCollectionCount(totalGcCount)
                    .gcCollectionTimeMs(totalGcTime);
        } catch (Throwable ignored) {}

        // 4. CPU & Host Resources
        try {
            builder.availableProcessors(OS_BEAN.getAvailableProcessors());
            builder.systemLoadAverage(OS_BEAN.getSystemLoadAverage());

            if (processCpuLoadMethod != null) {
                Object pCpu = processCpuLoadMethod.invoke(OS_BEAN);
                if (pCpu instanceof Number n) {
                    double val = n.doubleValue();
                    builder.processCpuLoadPercent(val >= 0 ? val * 100.0 : 0.0);
                }
            }

            if (systemCpuLoadMethod != null) {
                Object sCpu = systemCpuLoadMethod.invoke(OS_BEAN);
                if (sCpu instanceof Number n) {
                    double val = n.doubleValue();
                    builder.systemCpuLoadPercent(val >= 0 ? val * 100.0 : 0.0);
                }
            }

            long totalMem = 0;
            long freeMem = 0;
            if (totalMemoryMethod != null) {
                Object tm = totalMemoryMethod.invoke(OS_BEAN);
                if (tm instanceof Number n) totalMem = n.longValue();
            }
            if (freeMemoryMethod != null) {
                Object fm = freeMemoryMethod.invoke(OS_BEAN);
                if (fm instanceof Number n) freeMem = n.longValue();
            }

            if (totalMem > 0) {
                long usedMem = totalMem - freeMem;
                double memUsagePct = ((double) usedMem / totalMem) * 100.0;
                builder.totalPhysicalMemoryBytes(totalMem)
                        .freePhysicalMemoryBytes(freeMem)
                        .usedPhysicalMemoryBytes(usedMem)
                        .physicalMemoryUsagePercent(memUsagePct);
            }
        } catch (Throwable ignored) {}

        // 5. Host Disk Space
        try {
            String dirPath = config.getLogDirectory();
            File diskDir = new File(dirPath != null && !dirPath.isBlank() ? dirPath : ".");
            long totalDisk = diskDir.getTotalSpace();
            long usableDisk = diskDir.getUsableSpace();
            if (totalDisk > 0) {
                long usedDisk = totalDisk - usableDisk;
                double diskUsagePct = ((double) usedDisk / totalDisk) * 100.0;
                builder.totalDiskBytes(totalDisk)
                        .usableDiskBytes(usableDisk)
                        .diskUsagePercent(diskUsagePct);
            }
        } catch (Throwable ignored) {}

        // 6. Agent Internal Stats
        try {
            AsyncTraceLogger logger = AsyncTraceLogger.getInstance();
            if (logger != null) {
                builder.loggedTraceEvents(logger.getLoggedEventsCount())
                        .droppedTraceEvents(logger.getDroppedEventsCount())
                        .queuePendingEvents(logger.getQueueSize());
            }
            DynamicProbeManager probeMgr = DynamicProbeManager.getInstance();
            if (probeMgr != null) {
                builder.activeProbesCount(probeMgr.getActiveRules().size());
            }
        } catch (Throwable ignored) {}

        return builder.build();
    }
}
