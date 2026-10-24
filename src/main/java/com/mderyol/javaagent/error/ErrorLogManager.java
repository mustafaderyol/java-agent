package com.mderyol.javaagent.error;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import com.mderyol.javaagent.config.AgentConfig;
import com.mderyol.javaagent.context.SpanContext;
import com.mderyol.javaagent.context.TraceContext;
import com.mderyol.javaagent.logger.HostInfo;

/**
 * Manages runtime recording and remote toggle of method exception stack traces.
 */
public class ErrorLogManager {
    private static volatile ErrorLogManager INSTANCE;

    private volatile boolean enabled = true;
    private final AtomicLong loggedErrorCount = new AtomicLong(0);

    private ErrorLogManager(AgentConfig config) {
        if (config != null) {
            this.enabled = config.isErrorLoggingEnabled();
        }
    }

    public static ErrorLogManager getInstance() {
        if (INSTANCE == null) {
            synchronized (ErrorLogManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ErrorLogManager(AgentConfig.getInstance());
                }
            }
        }
        return INSTANCE;
    }

    public static synchronized void init(AgentConfig config) {
        if (INSTANCE == null) {
            INSTANCE = new ErrorLogManager(config);
        } else if (config != null) {
            INSTANCE.enabled = config.isErrorLoggingEnabled();
        }
    }

    public boolean isErrorLoggingEnabled() {
        return enabled;
    }

    public void setErrorLoggingEnabled(boolean enabled) {
        this.enabled = enabled;
        System.out.println("[JavaAgent] Method error stack trace logging dynamically set to: " + (enabled ? "ENABLED" : "DISABLED"));
    }

    public long getLoggedErrorCount() {
        return loggedErrorCount.get();
    }

    public void resetCount() {
        loggedErrorCount.set(0);
    }

    /**
     * Captures and logs the full stack trace of a failed method to error.log asynchronously.
     */
    public void logError(SpanContext span, Throwable thrown) {
        if (!enabled || span == null || thrown == null) return;

        try {
            ErrorTraceEvent event = new ErrorTraceEvent();
            event.setTimestamp(Instant.now().toString());
            event.setHost(HostInfo.getHostName());
            event.setJvmId(HostInfo.getJvmId());
            event.setPid(HostInfo.getPid());
            event.setTransactionId(TraceContext.getTransactionId());
            event.setSpanId(span.getSpanId());
            event.setParentSpanId(span.getParentSpanId());
            event.setDepth(span.getDepth());
            event.setClassName(span.getClassName());
            event.setMethodName(span.getMethodName());
            event.setExceptionClass(thrown.getClass().getName());
            event.setExceptionMessage(thrown.getMessage());

            StringWriter sw = new StringWriter();
            thrown.printStackTrace(new PrintWriter(sw));
            event.setStackTrace(sw.toString());

            if (thrown.getCause() != null) {
                Throwable cause = thrown.getCause();
                event.setCause(cause.getClass().getName() + ": " + cause.getMessage());
            }

            AsyncErrorLogger.getInstance().log(event);
            loggedErrorCount.incrementAndGet();
        } catch (Throwable ignored) {
            // Never disrupt user application when logging error stack traces
        }
    }
}
