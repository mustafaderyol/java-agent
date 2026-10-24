package com.mderyol.javaagent.logger;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraceEvent {
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
    private double durationMs;
    private String status; // "SUCCESS" or "ERROR"
    private String errorMessage;

    // Uzaktan Dynamic Probe ile yakalanan argümanlar ve yanıtlar
    private String requestPayload;
    private String responsePayload;

    public TraceEvent() {
        this.timestamp = Instant.now().toString();
        this.host = HostInfo.getHostName();
        this.jvmId = HostInfo.getJvmId();
        this.pid = HostInfo.getPid();
    }

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
    public double getDurationMs() { return durationMs; }
    public void setDurationMs(double durationMs) { this.durationMs = durationMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }
    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }
}
