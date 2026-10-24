package com.mderyol.javaagent.context;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import com.mderyol.javaagent.config.AgentConfig;
import com.mderyol.javaagent.logger.AsyncTraceLogger;
import com.mderyol.javaagent.logger.TraceEvent;

public class TraceContext {
    private static final ThreadLocal<TraceState> CONTEXT_HOLDER = new ThreadLocal<>();

    public static class TraceState {
        private String transactionId;
        private final Deque<SpanContext> callStack = new ArrayDeque<>();
        private int currentDepth = 0;
        private int activeProbeDepth = -1;

        public TraceState(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        public Deque<SpanContext> getCallStack() { return callStack; }
        public int getCurrentDepth() { return currentDepth; }
        public void incrementDepth() { this.currentDepth++; }
        public void decrementDepth() { if (this.currentDepth > 0) this.currentDepth--; }
        public int getActiveProbeDepth() { return activeProbeDepth; }
        public void setActiveProbeDepth(int activeProbeDepth) { this.activeProbeDepth = activeProbeDepth; }
    }

    /**
     * Dışarıdan veya HTTP Header'dan (örn: X-Transaction-Id / traceparent) gelen ID ile başlatma.
     */
    public static void initTransaction(String transactionId) {
        TraceState state = CONTEXT_HOLDER.get();
        if (state == null) {
            state = new TraceState(transactionId != null && !transactionId.isBlank()
                    ? transactionId
                    : UUID.randomUUID().toString());
            CONTEXT_HOLDER.set(state);
        } else if (transactionId != null && !transactionId.isBlank()) {
            state.setTransactionId(transactionId);
        }
    }

    public static String getTransactionId() {
        TraceState state = CONTEXT_HOLDER.get();
        return state != null ? state.getTransactionId() : null;
    }

    /**
     * Bu thread'de şu an bir üst metodun dinamik probe izlemesi aktif mi?
     * Eğer aktifse tüm alt çağrılar bu durumu devralır (Subtree Inheritance).
     */
    public static boolean isProbeActiveForCurrentThread() {
        TraceState state = CONTEXT_HOLDER.get();
        return state != null && state.getActiveProbeDepth() != -1;
    }

    /**
     * Bir metot probe kuralı ile eşleştiğinde alt çağrıları da kapsayacak şekilde probe başlatır.
     */
    public static boolean tryEnterProbe(int depth) {
        TraceState state = CONTEXT_HOLDER.get();
        if (state == null) return false;
        if (state.getActiveProbeDepth() == -1) {
            state.setActiveProbeDepth(depth);
            return true;
        }
        return false;
    }

    /**
     * Kök probe metodu çıkış yaptığında alt çağrı izleme durumunu kapatır.
     */
    public static void exitProbe(int depth) {
        TraceState state = CONTEXT_HOLDER.get();
        if (state != null && state.getActiveProbeDepth() == depth) {
            state.setActiveProbeDepth(-1);
        }
    }

    /**
     * Metot girişinde çağrılır. Span oluşturur ve stack'e ekler.
     */
    public static SpanContext startSpan(String className, String methodName) {
        AgentConfig config = AgentConfig.getInstance();
        TraceState state = CONTEXT_HOLDER.get();

        if (state == null) {
            state = new TraceState(UUID.randomUUID().toString());
            CONTEXT_HOLDER.set(state);
        }

        // Derin recursion bellek taşması koruması
        if (state.getCurrentDepth() >= config.getMaxCallDepth()) {
            return null;
        }

        SpanContext parentSpan = state.getCallStack().peek();
        String parentSpanId = parentSpan != null ? parentSpan.getSpanId() : null;
        String spanId = Long.toHexString(System.nanoTime()) + Integer.toHexString(state.getCurrentDepth());

        SpanContext span = new SpanContext(
                spanId,
                parentSpanId,
                className,
                methodName,
                System.nanoTime(),
                state.getCurrentDepth()
        );

        state.getCallStack().push(span);
        state.incrementDepth();
        return span;
    }

    /**
     * Metot çıkışında (finally bloğunda) çağrılır.
     * Stack boşaldığında ThreadLocal'ı kesin olarak siler (ZERO LEAK).
     */
    public static void endSpan(SpanContext span, String status, String errorMessage,
                               String requestPayload, String responsePayload) {
        TraceState state = CONTEXT_HOLDER.get();
        if (state == null || span == null) return;

        try {
            state.decrementDepth();
            if (!state.getCallStack().isEmpty()) {
                // Eşleşen span'i pop et
                SpanContext top = state.getCallStack().peek();
                if (top == span) {
                    state.getCallStack().pop();
                } else {
                    // Güvenlik: Beklenmeyen sıra dışı çağrılarda stack'ten ilgiliyi çıkar
                    state.getCallStack().remove(span);
                }
            }

            long durationNanos = System.nanoTime() - span.getStartTimeNanos();
            double durationMs = durationNanos / 1_000_000.0;

            TraceEvent event = new TraceEvent();
            event.setTransactionId(state.getTransactionId());
            event.setSpanId(span.getSpanId());
            event.setParentSpanId(span.getParentSpanId());
            event.setDepth(span.getDepth());
            event.setClassName(span.getClassName());
            event.setMethodName(span.getMethodName());
            event.setDurationMs(durationMs);
            event.setStatus(status);
            event.setErrorMessage(errorMessage);
            event.setRequestPayload(requestPayload);
            event.setResponsePayload(responsePayload);

            AsyncTraceLogger.getInstance().log(event);

        } finally {
            // EN KRİTİK NOKTA: Kök metoda dönüldüğünde ThreadLocal TAMAMEN temizlenir!
            // Tomcat/Netty thread pool'larında OOM oluşmasını engelleyen kilit mekanizma.
            if (state.getCallStack().isEmpty()) {
                CONTEXT_HOLDER.remove();
            }
        }
    }

    /**
     * Manuel transaction temizliği gerektiğinde (örn: Servlet Filter çıkışı).
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }
}
