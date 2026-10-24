package com.mderyol.javaagent.interceptor;

import net.bytebuddy.asm.Advice;
import com.mderyol.javaagent.context.SpanContext;
import com.mderyol.javaagent.context.TraceContext;
import com.mderyol.javaagent.probe.DynamicProbeManager;
import com.mderyol.javaagent.probe.ProbeCaptureEvent;
import com.mderyol.javaagent.probe.ProbeCaptureLogger;
import com.mderyol.javaagent.probe.ProbeRule;

public class MethodTraceAdvice {

    public static class EnterContext {
        public final SpanContext span;
        public final boolean isProbeRoot;
        public final String requestPayload;

        public EnterContext(SpanContext span, boolean isProbeRoot, String requestPayload) {
            this.span = span;
            this.isProbeRoot = isProbeRoot;
            this.requestPayload = requestPayload;
        }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static EnterContext onEnter(
            @Advice.Origin("#t") String className,
            @Advice.Origin("#m") String methodName,
            @Advice.AllArguments Object[] args) {

        SpanContext span = TraceContext.startSpan(className, methodName);
        if (span == null) {
            return null;
        }

        DynamicProbeManager probeManager = DynamicProbeManager.getInstance();
        boolean shouldCapture = false;
        boolean isProbeRoot = false;

        // 1. Üstteki bir servis/metot probe altında mı? (Tüm alt çağrılar otomatik kapsanır)
        if (TraceContext.isProbeActiveForCurrentThread()) {
            shouldCapture = true;
            isProbeRoot = false;
        } else {
            // 2. Bu metot için tanımlı doğrudan bir kural var mı?
            ProbeRule rule = probeManager.getActiveRule(className, methodName);
            if (rule != null && rule.tryConsumeCapture()) {
                shouldCapture = true;
                isProbeRoot = TraceContext.tryEnterProbe(span.getDepth());
            }
        }

        String requestPayload = null;
        if (shouldCapture) {
            requestPayload = probeManager.serializePayload(args);
        }

        return new EnterContext(span, isProbeRoot, requestPayload);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.Enter EnterContext context,
            @Advice.Return(typing = net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC) Object returned,
            @Advice.Thrown Throwable thrown) {

        if (context == null || context.span == null) {
            return;
        }

        try {
            String status = (thrown == null) ? "SUCCESS" : "ERROR";
            String errorMessage = (thrown != null) ? thrown.getClass().getName() + ": " + thrown.getMessage() : null;

            // 1. Normal trace.log: ASLA request/response yazmaz (Hafif ve hızlı çağrı ağacı)
            TraceContext.endSpan(context.span, status, errorMessage, null, null);

            // 2. Eğer uzaktan probe AÇILDIYSA (veya üst servisten devralındıysa): probe-capture.log'a yazılır
            if (context.requestPayload != null) {
                String responsePayload;
                if (thrown != null) {
                    responsePayload = "{\"exception\": \"" + thrown.getClass().getName() + "\", \"message\": \"" + thrown.getMessage() + "\"}";
                } else {
                    responsePayload = DynamicProbeManager.getInstance().serializePayload(returned);
                }

                long durationNanos = System.nanoTime() - context.span.getStartTimeNanos();
                double durationMs = durationNanos / 1_000_000.0;

                ProbeCaptureEvent capture = new ProbeCaptureEvent();
                capture.setTransactionId(TraceContext.getTransactionId());
                capture.setSpanId(context.span.getSpanId());
                capture.setParentSpanId(context.span.getParentSpanId());
                capture.setDepth(context.span.getDepth());
                capture.setClassName(context.span.getClassName());
                capture.setMethodName(context.span.getMethodName());
                capture.setDurationMs(durationMs);
                capture.setStatus(status);
                capture.setRequestPayload(context.requestPayload);
                capture.setResponsePayload(responsePayload);

                ProbeCaptureLogger.getInstance().log(capture);
            }
        } finally {
            if (context.isProbeRoot) {
                TraceContext.exitProbe(context.span.getDepth());
            }
        }
    }
}
