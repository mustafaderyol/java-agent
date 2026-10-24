package com.mderyol.javaagent.error;

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
 * Asynchronous, non-blocking logger for recording method exceptions and full stack traces
 * into a dedicated rotating file (error-*.log), isolated from trace.log.
 */
public class AsyncErrorLogger {
    private static volatile AsyncErrorLogger INSTANCE;

    private final BlockingQueue<ErrorTraceEvent> queue = new ArrayBlockingQueue<>(5000);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AgentConfig config;

    private BufferedWriter writer;
    private Path logFile;
    private long currentFileBytes = 0;

    private AsyncErrorLogger(AgentConfig config) {
        this.config = config;
        initFile();
        startWorker();
    }

    public static AsyncErrorLogger getInstance() {
        if (INSTANCE == null) {
            synchronized (AsyncErrorLogger.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AsyncErrorLogger(AgentConfig.getInstance());
                }
            }
        }
        return INSTANCE;
    }

    public static synchronized void init(AgentConfig config) {
        if (INSTANCE == null) {
            INSTANCE = new AsyncErrorLogger(config);
        }
    }

    public String getLogFileName() {
        return config.getEffectiveErrorFileName();
    }

    public String getAbsolutePath() {
        return (logFile != null) ? logFile.toAbsolutePath().toString() : "";
    }

    private void initFile() {
        try {
            Path dir = Paths.get(config.getLogDirectory());
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            this.logFile = dir.resolve(config.getEffectiveErrorFileName());
            if (Files.exists(logFile)) {
                this.currentFileBytes = Files.size(logFile);
            } else {
                this.currentFileBytes = 0;
            }
            this.writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("[JavaAgent] Could not initialize error.log: " + e.getMessage());
        }
    }

    public void log(ErrorTraceEvent event) {
        if (event == null || !running.get()) return;
        queue.offer(event);
    }

    private void startWorker() {
        Thread worker = new Thread(() -> {
            List<ErrorTraceEvent> batch = new ArrayList<>(50);
            while (running.get() || !queue.isEmpty()) {
                try {
                    ErrorTraceEvent first = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (first != null) {
                        batch.add(first);
                        queue.drainTo(batch, 49);
                        writeBatch(batch);
                        batch.clear();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[JavaAgent] Error writing to error.log: " + e.getMessage());
                }
            }
            flushRemaining();
        }, "JavaAgent-AsyncErrorLogger");

        worker.setDaemon(true);
        worker.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            flushRemaining();
        }));
    }

    private synchronized void writeBatch(List<ErrorTraceEvent> batch) {
        if (writer == null || batch.isEmpty()) return;

        try {
            for (ErrorTraceEvent event : batch) {
                String line = mapper.writeValueAsString(event);
                byte[] bytes = line.getBytes(StandardCharsets.UTF_8);

                checkRotation(bytes.length + 1);

                writer.write(line);
                writer.newLine();
                currentFileBytes += bytes.length + 1;
            }
            writer.flush();
        } catch (Exception e) {
            System.err.println("[JavaAgent] Failed to write error batch: " + e.getMessage());
        }
    }

    private void checkRotation(long nextBytes) {
        long maxSizeBytes = config.getErrorLogMaxFileSizeBytes();
        if (currentFileBytes + nextBytes > maxSizeBytes) {
            rotateFiles();
        }
    }

    private synchronized void rotateFiles() {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }

            int maxBackups = config.getErrorLogMaxBackupFiles();
            for (int i = maxBackups - 1; i >= 1; i--) {
                Path src = logFile.resolveSibling(logFile.getFileName() + "." + i);
                Path dst = logFile.resolveSibling(logFile.getFileName() + "." + (i + 1));
                if (Files.exists(src)) {
                    Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            Path firstBackup = logFile.resolveSibling(logFile.getFileName() + ".1");
            if (Files.exists(logFile)) {
                Files.move(logFile, firstBackup, StandardCopyOption.REPLACE_EXISTING);
            }

            this.writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            this.currentFileBytes = 0;
        } catch (IOException e) {
            System.err.println("[JavaAgent] Error log file rotation failed: " + e.getMessage());
            try {
                this.writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {}
        }
    }

    private synchronized void flushRemaining() {
        try {
            List<ErrorTraceEvent> remaining = new ArrayList<>();
            queue.drainTo(remaining);
            if (!remaining.isEmpty() && writer != null) {
                for (ErrorTraceEvent event : remaining) {
                    writer.write(mapper.writeValueAsString(event));
                    writer.newLine();
                }
                writer.flush();
            }
            if (writer != null) {
                writer.close();
            }
        } catch (Exception ignored) {}
    }
}
