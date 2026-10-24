package com.mderyol.javaagent.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorTraceEvent {
    private String timestamp;
    private String host;
    private String jvmId;
    private long pid;
    private String transactionId;
    private String spanId;
    private String parentSpanId;
    private int depth;
    private String className;
    private String methodName;
    private String exceptionClass;
    private String exceptionMessage;
    private String stackTrace;
    private String cause;
    private String status = "ERROR";
    private double durationMs = 0.0;

    public ErrorTraceEvent() {}

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getDurationMs() { return durationMs; }
    public void setDurationMs(double durationMs) { this.durationMs = durationMs; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public String getJvmId() { return jvmId; }
    public void setJvmId(String jvmId) { this.jvmId = jvmId; }

    public long getPid() { return pid; }
    public void setPid(long pid) { this.pid = pid; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }

    public String getParentSpanId() { return parentSpanId; }
    public void setParentSpanId(String parentSpanId) { this.parentSpanId = parentSpanId; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getExceptionClass() { return exceptionClass; }
    public void setExceptionClass(String exceptionClass) { this.exceptionClass = exceptionClass; }

    public String getExceptionMessage() { return exceptionMessage; }
    public void setExceptionMessage(String exceptionMessage) { this.exceptionMessage = exceptionMessage; }

    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }

    public String getCause() { return cause; }
    public void setCause(String cause) { this.cause = cause; }
}
