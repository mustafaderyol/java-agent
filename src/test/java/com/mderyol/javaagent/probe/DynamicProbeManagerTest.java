package com.mderyol.javaagent.probe;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DynamicProbeManagerTest {

    private DynamicProbeManager probeManager;

    @BeforeEach
    public void setup() {
        probeManager = DynamicProbeManager.getInstance();
        probeManager.clearAll();
    }

    @Test
    public void testRegisterAndConsumeProbe() {
        // 1. Probe Kaydı (60 saniye, 2 yakalama hakkı)
        probeManager.registerProbe("com.mderyol.service.PaymentService", "pay", 60, 2);

        // Aktif kuralı getir
        ProbeRule rule = probeManager.getActiveRule("com.mderyol.service.PaymentService", "pay");
        assertNotNull(rule);
        assertEquals(2, rule.getRemainingCaptures());

        // 1. Yakalama
        assertTrue(rule.tryConsumeCapture());
        assertEquals(1, rule.getRemainingCaptures());

        // 2. Yakalama
        assertTrue(rule.tryConsumeCapture());
        assertEquals(0, rule.getRemainingCaptures());

        // 3. Yakalama limiti dolduğu için reddedilmeli
        assertFalse(rule.tryConsumeCapture());

        // Kural limiti bittiği için artık getActiveRule null dönmeli (otomatik temizleme)
        assertNull(probeManager.getActiveRule("com.mderyol.service.PaymentService", "pay"));
    }

    @Test
    public void testPayloadSerialization() {
        Map<String, Object> req = Map.of("userId", 42, "amount", 199.99);
        String json = probeManager.serializePayload(req);
        assertNotNull(json);
        assertTrue(json.contains("userId"));
        assertTrue(json.contains("42"));
    }
}
