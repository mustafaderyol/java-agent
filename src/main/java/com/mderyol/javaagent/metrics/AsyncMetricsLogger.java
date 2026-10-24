package com.mderyol.javaagent.metrics;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mderyol.javaagent.config.AgentConfig;

/**
 * Scheduled asynchronous logger that samples and appends System & JVM metrics
 * to a dedicated rotating NDJSON log file with minimal CPU and memory footprint.
 */
public class AsyncMetricsLogger {

    private static volatile AsyncMetricsLogger INSTANCE;

    private final AgentConfig config;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private volatile SystemMetricsSnapshot latestSnapshot;

    private BufferedWriter writer;
    private Path currentLogFile;
    private long currentFileBytes = 0;

    private AsyncMetricsLogger(AgentConfig config) {
        this.config = config;
        this.mapper = new ObjectMapper();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "JavaAgent-MetricsLogger");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

        initFileSystem();

        // Perform an initial lightweight collection immediately
        try {
            this.latestSnapshot = MetricsCollector.collect(config);
            writeSnapshot(this.latestSnapshot);
        } catch (Throwable ignored) {}

        // Schedule periodic collection
        int interval = Math.max(5, config.getMetricsIntervalSeconds());
        this.scheduler.scheduleAtFixedRate(this::collectAndLog, interval, interval, TimeUnit.SECONDS);

        registerShutdownHook();
    }

    public static synchronized void init(AgentConfig config) {
        if (INSTANCE == null && config.isMetricsEnabled()) {
            INSTANCE = new AsyncMetricsLogger(config);
        }
    }

    public static AsyncMetricsLogger getInstance() {
        return INSTANCE;
    }

    public SystemMetricsSnapshot getLatestSnapshot() {
        if (latestSnapshot == null) {
            latestSnapshot = MetricsCollector.collect(config);
        }
        return latestSnapshot;
    }

    private void collectAndLog() {
        if (!running.get()) return;
        try {
            SystemMetricsSnapshot snapshot = MetricsCollector.collect(config);
            this.latestSnapshot = snapshot;
            writeSnapshot(snapshot);
        } catch (Throwable t) {
            // Failsafe: never disrupt host application
        }
    }

    private synchronized void writeSnapshot(SystemMetricsSnapshot snapshot) {
        if (snapshot == null || writer == null) return;
        try {
            checkRotation();

            String jsonLine = mapper.writeValueAsString(snapshot.toMap()) + "\n";
            byte[] bytes = jsonLine.getBytes(StandardCharsets.UTF_8);

            writer.write(jsonLine);
            writer.flush();
            currentFileBytes += bytes.length;
        } catch (Throwable ignored) {}
    }

    private void initFileSystem() {
        try {
            Path logDir = Paths.get(config.getLogDirectory());
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
            this.currentLogFile = logDir.resolve(config.getEffectiveMetricsFileName());
            if (Files.exists(currentLogFile)) {
                this.currentFileBytes = Files.size(currentLogFile);
            } else {
                this.currentFileBytes = 0;
            }
            this.writer = Files.newBufferedWriter(currentLogFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable t) {
            System.err.println("[JavaAgent] Failed to init metrics log file: " + t.getMessage());
        }
    }

    private void checkRotation() {
        try {
            long maxBytes = config.getMetricsMaxFileSizeBytes();
            if (currentFileBytes < maxBytes) return;

            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (Throwable ignored) {}
            }

            int maxBackups = Math.max(1, config.getMetricsMaxBackupFiles());
            Path oldest = Paths.get(currentLogFile.toString() + "." + maxBackups);
            if (Files.exists(oldest)) {
                Files.deleteIfExists(oldest);
            }

            for (int i = maxBackups - 1; i >= 1; i--) {
                Path src = Paths.get(currentLogFile.toString() + "." + i);
                Path dst = Paths.get(currentLogFile.toString() + "." + (i + 1));
                if (Files.exists(src)) {
                    Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            Path firstBackup = Paths.get(currentLogFile.toString() + ".1");
            if (Files.exists(currentLogFile)) {
                Files.move(currentLogFile, firstBackup, StandardCopyOption.REPLACE_EXISTING);
            }

            this.writer = Files.newBufferedWriter(currentLogFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            this.currentFileBytes = 0;
        } catch (Throwable t) {
            System.err.println("[JavaAgent] Failed to rotate metrics log file: " + t.getMessage());
        }
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) return;
        try {
            scheduler.shutdown();
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        } catch (Throwable ignored) {}
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                stop();
            } catch (Throwable ignored) {}
        }, "JavaAgent-MetricsLogger-Shutdown"));
    }
}
