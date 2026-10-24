package com.mderyol.javaagent.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mderyol.javaagent.config.AgentConfig;
import com.mderyol.javaagent.context.SpanContext;
import com.mderyol.javaagent.context.TraceContext;

import static org.junit.jupiter.api.Assertions.*;

public class ErrorLogManagerTest {

    private ErrorLogManager manager;

    @BeforeEach
    void setUp() {
        AgentConfig config = new AgentConfig();
        config.setErrorLoggingEnabled(true);
        ErrorLogManager.init(config);
        this.manager = ErrorLogManager.getInstance();
        this.manager.setErrorLoggingEnabled(true);
        this.manager.resetCount();
    }

    @Test
    void testDefaultEnabledAndToggle() {
        assertTrue(manager.isErrorLoggingEnabled());

        manager.setErrorLoggingEnabled(false);
        assertFalse(manager.isErrorLoggingEnabled());

        manager.setErrorLoggingEnabled(true);
        assertTrue(manager.isErrorLoggingEnabled());
    }

    @Test
    void testLogErrorCapturesStackTraceAndIncrementsCount() {
        SpanContext span = new SpanContext("span-100", "span-root", "com.example.OrderService", "processPayment", System.nanoTime(), 1);
        TraceContext.initTransaction("tx-999");

        Exception simulatedError = new IllegalArgumentException("Invalid credit card CVV", new NullPointerException("Null card token"));

        assertEquals(0, manager.getLoggedErrorCount());
        manager.logError(span, simulatedError);

        assertEquals(1, manager.getLoggedErrorCount());

        TraceContext.clear();
    }

    @Test
    void testLogErrorIgnoredWhenDisabled() {
        manager.setErrorLoggingEnabled(false);

        SpanContext span = new SpanContext("span-101", null, "com.example.OrderService", "checkout", System.nanoTime(), 0);
        Exception error = new RuntimeException("Database timeout");

        manager.logError(span, error);
        assertEquals(0, manager.getLoggedErrorCount());
    }

    @Test
    void testErrorTraceEventJsonSerialization() throws Exception {
        ErrorTraceEvent event = new ErrorTraceEvent();
        event.setTimestamp("2026-10-25T01:00:00Z");
        event.setHost("test-host");
        event.setJvmId("node-1");
        event.setPid(12345);
        event.setTransactionId("tx-123");
        event.setSpanId("sp-456");
        event.setClassName("com.example.PaymentService");
        event.setMethodName("charge");
        event.setExceptionClass("java.lang.IllegalStateException");
        event.setExceptionMessage("Connection refused");
        event.setStackTrace("java.lang.IllegalStateException: Connection refused\n\tat com.example.PaymentService.charge(PaymentService.java:42)");

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(event);

        assertTrue(json.contains("\"exceptionClass\":\"java.lang.IllegalStateException\""));
        assertTrue(json.contains("\"exceptionMessage\":\"Connection refused\""));
        assertTrue(json.contains("\"stackTrace\":"));
        assertTrue(json.contains("\"methodName\":\"charge\""));
    }
}
