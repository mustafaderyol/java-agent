package com.mderyol.javaagent.context;

public class SpanContext {
    private final String spanId;
    private final String parentSpanId;
    private final String className;
    private final String methodName;
    private final long startTimeNanos;
    private final int depth;

    public SpanContext(String spanId, String parentSpanId, String className, String methodName, long startTimeNanos, int depth) {
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.className = className;
        this.methodName = methodName;
        this.startTimeNanos = startTimeNanos;
        this.depth = depth;
    }

    public String getSpanId() { return spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public long getStartTimeNanos() { return startTimeNanos; }
    public int getDepth() { return depth; }
}
