package com.mderyol.javaagent.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TraceContextTest {

    @BeforeEach
    @AfterEach
    public void cleanUp() {
        TraceContext.clear();
    }

    @Test
    public void testCallHierarchyAndParentSpanId() {
        // 1. Root Metot Başlar
        SpanContext rootSpan = TraceContext.startSpan("OrderController", "createOrder");
        assertNotNull(rootSpan);
        assertNull(rootSpan.getParentSpanId(), "Root span must not have a parentSpanId");
        assertEquals(0, rootSpan.getDepth());

        String transactionId = TraceContext.getTransactionId();
        assertNotNull(transactionId);

        // 2. Child 1: Service Metodu Başlar
        SpanContext serviceSpan = TraceContext.startSpan("OrderService", "processPayment");
        assertNotNull(serviceSpan);
        assertEquals(rootSpan.getSpanId(), serviceSpan.getParentSpanId(), "Child must have root's spanId as parentSpanId");
        assertEquals(1, serviceSpan.getDepth());

        // 3. Child 2: Repository Metodu Başlar
        SpanContext repoSpan = TraceContext.startSpan("PaymentRepository", "saveTransaction");
        assertNotNull(repoSpan);
        assertEquals(serviceSpan.getSpanId(), repoSpan.getParentSpanId(), "Grandchild must have child's spanId as parentSpanId");
        assertEquals(2, repoSpan.getDepth());

        // 4. Repo Metodu Biter
        TraceContext.endSpan(repoSpan, "SUCCESS", null, null, "{\"saved\": true}");

        // 5. Service Metodu Biter
        TraceContext.endSpan(serviceSpan, "SUCCESS", null, null, "{\"status\": \"PAID\"}");

        // 6. Root Metodu Biter
        TraceContext.endSpan(rootSpan, "SUCCESS", null, null, "{\"httpStatus\": 200}");

        // 7. SIFIR LEAK KONTROLÜ: Root çıkışında ThreadLocal TAMAMEN temizlenmiş olmalı!
        assertNull(TraceContext.getTransactionId(), "ThreadLocal must be cleared after root method exits");
    }

    @Test
    public void testExplicitTransactionIdPropagation() {
        String externalTxId = "MY-DISTRIBUTED-TRACE-12345";
        TraceContext.initTransaction(externalTxId);

        SpanContext rootSpan = TraceContext.startSpan("GatewayFilter", "filter");
        assertEquals(externalTxId, TraceContext.getTransactionId());

        TraceContext.endSpan(rootSpan, "SUCCESS", null, null, null);
        assertNull(TraceContext.getTransactionId(), "Should be cleaned up after root finishes");
    }
}
