package com.mderyol.javaagent.probe;

import java.util.concurrent.atomic.AtomicInteger;

public class ProbeRule {
    private final String className;
    private final String methodName; // null veya "*" ise o class'taki tüm metotlar
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final int totalCapturesAllowed;
    private final AtomicInteger remainingCaptures;

    public ProbeRule(String className, String methodName, int durationSeconds, int maxCaptures) {
        this.className = className;
        this.methodName = (methodName == null || methodName.isBlank() || methodName.equals("*")) ? null : methodName;
        this.createdAtMillis = System.currentTimeMillis();
        this.expiresAtMillis = this.createdAtMillis + (durationSeconds * 1000L);
        this.totalCapturesAllowed = maxCaptures > 0 ? maxCaptures : 100;
        this.remainingCaptures = new AtomicInteger(this.totalCapturesAllowed);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMillis || remainingCaptures.get() <= 0;
    }

    public boolean tryConsumeCapture() {
        while (true) {
            int current = remainingCaptures.get();
            if (current <= 0 || System.currentTimeMillis() >= expiresAtMillis) {
                return false;
            }
            if (remainingCaptures.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public long getExpiresAtMillis() { return expiresAtMillis; }
    public int getTotalCapturesAllowed() { return totalCapturesAllowed; }
    public int getRemainingCaptures() { return remainingCaptures.get(); }
    public long getRemainingSeconds() {
        long rem = (expiresAtMillis - System.currentTimeMillis()) / 1000L;
        return Math.max(0, rem);
    }
}
