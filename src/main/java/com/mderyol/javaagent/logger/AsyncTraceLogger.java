package com.mderyol.javaagent.logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mderyol.javaagent.config.AgentConfig;

public class AsyncTraceLogger {
    private static volatile AsyncTraceLogger INSTANCE;

    private final BlockingQueue<TraceEvent> eventQueue;
    private final AgentConfig config;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong loggedEventsCount = new AtomicLong(0);
    private final AtomicLong droppedEventsCount = new AtomicLong(0);

    private BufferedWriter writer;
    private Path currentLogFile;
    private long currentFileBytes = 0;
    private Thread workerThread;

    private AsyncTraceLogger(AgentConfig config) {
        this.config = config;
        this.eventQueue = new ArrayBlockingQueue<>(config.getQueueCapacity());
        this.objectMapper = new ObjectMapper();
        initFileSystem();
        startWorker();
        registerShutdownHook();
    }

    public static AsyncTraceLogger getInstance() {
        if (INSTANCE == null) {
            synchronized (AsyncTraceLogger.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AsyncTraceLogger(AgentConfig.getInstance());
                }
            }
        }
        return INSTANCE;
    }

    public static synchronized void init(AgentConfig config) {
        if (INSTANCE == null) {
            INSTANCE = new AsyncTraceLogger(config);
        }
    }

    /**
     * Non-blocking, zero-allocation drop policy to prevent OutOfMemoryError.
     */
    public void log(TraceEvent event) {
        if (event == null || !running.get()) return;

        // Kuyruk doluysa thread'i BLOKE ETMEZ ve RAM'i ŞİŞİRMEZ!
        if (!eventQueue.offer(event)) {
            droppedEventsCount.incrementAndGet();
        }
    }

    private void initFileSystem() {
        try {
            Path logDir = Paths.get(config.getLogDirectory());
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
            this.currentLogFile = logDir.resolve(config.getEffectiveTraceFileName());
            if (Files.exists(currentLogFile)) {
                this.currentFileBytes = Files.size(currentLogFile);
            } else {
                this.currentFileBytes = 0;
            }
            this.writer = Files.newBufferedWriter(currentLogFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("[JavaAgent] Failed to init log file: " + e.getMessage());
        }
    }

    private void startWorker() {
        this.workerThread = new Thread(() -> {
            List<TraceEvent> batch = new ArrayList<>(config.getBatchSize());
            long lastFlushTime = System.currentTimeMillis();

            while (running.get() || !eventQueue.isEmpty()) {
                try {
                    TraceEvent first = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (first != null) {
                        batch.add(first);
                        eventQueue.drainTo(batch, config.getBatchSize() - 1);
                    }

                    if (!batch.isEmpty()) {
                        writeBatch(batch);
                        batch.clear();
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastFlushTime >= config.getFlushIntervalMs()) {
                        if (writer != null) {
                            writer.flush();
                        }
                        lastFlushTime = now;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[JavaAgent] Error in AsyncTraceLogger worker: " + e.getMessage());
                }
            }

            // Kalan son kayıtları flush et
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException ignored) {}
            }
        }, "JavaAgent-AsyncTraceLogger");

        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    private void writeBatch(List<TraceEvent> batch) {
        if (writer == null) return;

        try {
            for (TraceEvent event : batch) {
                String json = objectMapper.writeValueAsString(event);
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

                // Dosya boyutu kontrolü (bellekteki sayaç üzerinden, disk syscall yapmadan!)
                if (currentFileBytes + bytes.length > config.getMaxFileSizeBytes()) {
                    rotateLogFile();
                }

                writer.write(json);
                writer.newLine();
                currentFileBytes += bytes.length + 1; // 1 byte for newline
                loggedEventsCount.incrementAndGet();
            }
        } catch (Exception e) {
            System.err.println("[JavaAgent] Error writing trace batch: " + e.getMessage());
        }
    }

    private void rotateLogFile() {
        try {
            writer.flush();
            writer.close();

            Path logDir = Paths.get(config.getLogDirectory());
            int maxBackups = config.getMaxBackupFiles();

            // En eski dosyayı sil (disk doluluk koruması)
            Path oldest = logDir.resolve(config.getLogFileName() + "." + maxBackups);
            Files.deleteIfExists(oldest);

            // Eski dosyaları kaydır (trace.log.1, trace.log.2 ...)
            for (int i = maxBackups - 1; i >= 1; i--) {
                Path src = logDir.resolve(config.getLogFileName() + "." + i);
                Path dest = logDir.resolve(config.getLogFileName() + "." + (i + 1));
                if (Files.exists(src)) {
                    Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            Path backup = logDir.resolve(config.getLogFileName() + ".1");
            if (Files.exists(currentLogFile)) {
                Files.move(currentLogFile, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            this.writer = Files.newBufferedWriter(currentLogFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            this.currentFileBytes = 0;
        } catch (Exception e) {
            System.err.println("[JavaAgent] Error rotating log file: " + e.getMessage());
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            if (workerThread != null) {
                try {
                    workerThread.join(3000);
                } catch (InterruptedException ignored) {}
            }
        }));
    }

    public long getLoggedEventsCount() { return loggedEventsCount.get(); }
    public long getDroppedEventsCount() { return droppedEventsCount.get(); }
    public int getQueueSize() { return eventQueue.size(); }
}
