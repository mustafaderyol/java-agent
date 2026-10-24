package com.mderyol.javaagent.probe;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mderyol.javaagent.config.AgentConfig;

/**
 * Asynchronous logger that records dynamically probed Request and Response payloads
 * to a dedicated rotating file (probe-capture-*.log), isolated from trace.log.
 */
public class ProbeCaptureLogger {
    private static volatile ProbeCaptureLogger INSTANCE;

    private final BlockingQueue<ProbeCaptureEvent> queue = new ArrayBlockingQueue<>(5000);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AgentConfig config;

    private BufferedWriter writer;
    private Path logFile;
    private long currentFileBytes = 0;

    private ProbeCaptureLogger(AgentConfig config) {
        this.config = config;
        initFile();
        startWorker();
    }

    public static ProbeCaptureLogger getInstance() {
        if (INSTANCE == null) {
            synchronized (ProbeCaptureLogger.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ProbeCaptureLogger(AgentConfig.getInstance());
                }
            }
        }
        return INSTANCE;
    }

    public String getLogFileName() {
        return config.getEffectiveProbeFileName();
    }

    private void initFile() {
        try {
            Path dir = Paths.get(config.getLogDirectory());
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            this.logFile = dir.resolve(config.getEffectiveProbeFileName());
            if (Files.exists(logFile)) {
                this.currentFileBytes = Files.size(logFile);
            } else {
                this.currentFileBytes = 0;
            }
            this.writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("[JavaAgent] Could not initialize probe-capture.log: " + e.getMessage());
        }
    }

    public void log(ProbeCaptureEvent event) {
        if (event == null || !running.get()) return;
        queue.offer(event);
    }

    private void startWorker() {
        Thread worker = new Thread(() -> {
            List<ProbeCaptureEvent> batch = new ArrayList<>(100);
            while (running.get() || !queue.isEmpty()) {
                try {
                    ProbeCaptureEvent first = queue.poll(200, TimeUnit.MILLISECONDS);
                    if (first != null) {
                        batch.add(first);
                        queue.drainTo(batch, 99);
                    }
                    if (!batch.isEmpty() && writer != null) {
                        for (ProbeCaptureEvent ev : batch) {
                            String json = mapper.writeValueAsString(ev);
                            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

                            // Size rotation check
                            if (currentFileBytes + bytes.length > config.getMaxFileSizeBytes()) {
                                rotateFile();
                            }

                            writer.write(json);
                            writer.newLine();
                            currentFileBytes += bytes.length + 1;
                        }
                        writer.flush();
                        batch.clear();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[JavaAgent] Error writing to probe-capture.log: " + e.getMessage());
                }
            }
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException ignored) {}
            }
        }, "JavaAgent-ProbeCaptureWriter");
        worker.setDaemon(true);
        worker.start();
    }

    private void rotateFile() {
        try {
            writer.flush();
            writer.close();

            Path dir = Paths.get(config.getLogDirectory());
            int maxBackups = config.getMaxBackupFiles();

            // Delete oldest backup file
            Path oldest = dir.resolve("probe-capture.log." + maxBackups);
            Files.deleteIfExists(oldest);

            // Shift older backups (.1 -> .2)
            for (int i = maxBackups - 1; i >= 1; i--) {
                Path src = dir.resolve("probe-capture.log." + i);
                Path dest = dir.resolve("probe-capture.log." + (i + 1));
                if (Files.exists(src)) {
                    Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            Path backup1 = dir.resolve("probe-capture.log.1");
            if (Files.exists(logFile)) {
                Files.move(logFile, backup1, StandardCopyOption.REPLACE_EXISTING);
            }

            this.writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            this.currentFileBytes = 0;
        } catch (Exception e) {
            System.err.println("[JavaAgent] Error rotating probe-capture.log: " + e.getMessage());
        }
    }
}
