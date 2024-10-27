package com.mderyol.javaagent.logger;

import com.mderyol.javaagent.config.Argument;
import com.mderyol.javaagent.config.LogConfig;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class Logger {
    private static final String FILE_EXTENSION = ".log";
    private static Logger instance;
    private static List<LogDTO> queue = new ArrayList<>();
    private static final Object QUEUE_LOCK = new Object();
    private static final Object WAIT_LOCK = new Object();
    private static String fileNameSuffix = "";

    private Logger() {
    }

    public static Logger getInstance() {
        if (instance == null) {
            fileNameSuffix = "" + System.currentTimeMillis();
            instance = new Logger();
            new Thread(new LogThread()).start();
        }
        return instance;
    }

    public void log(LogDTO logDTO) {
        synchronized (QUEUE_LOCK) {
            queue.add(logDTO);
        }
        if (queue.size() > Argument.getInstance().getConfig().getLog().getNotifyLimit()) {
            synchronized (WAIT_LOCK) {
                WAIT_LOCK.notify();
            }
        }
    }

    static class LogThread implements Runnable {
        @Override
        public void run() {
            LogConfig logConfig = Argument.getInstance().getConfig().getLog();
            System.out.println("AGENT - Log Thread Running");
            while (true) {
                List<LogDTO> queueToRun = null;
                synchronized (QUEUE_LOCK) {
                    queueToRun = queue;
                    queue = new ArrayList<>();
                }
                if (!queueToRun.isEmpty()) {
                    String outputFileName = logConfig.getOutputPath() + fileNameSuffix + FILE_EXTENSION;
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName, true))) {
                        for (LogDTO logDTO : queueToRun) {
                            writer.write(logDTO.toString());
                            writer.newLine();
                            writer.flush();
                            if (rotateFileName(outputFileName, logConfig)) {
                                fileNameSuffix = "" + System.currentTimeMillis();
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("AGENT - File Write Exception: " + e.toString());
                    }
                }
                synchronized (WAIT_LOCK) {
                    try {
                        WAIT_LOCK.wait(logConfig.getWaitTime());
                    } catch (Exception e) {
                    }
                }
            }
        }

        private boolean rotateFileName(String outputFileName, LogConfig logConfig) {
            File file = new File(outputFileName);
            return file.length() >= logConfig.getMaxFileSize();
        }
    }
}
