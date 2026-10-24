package com.mderyol.javaagent.metrics;

import org.junit.jupiter.api.Test;
import com.mderyol.javaagent.config.AgentConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MetricsCollectorTest {

    @Test
    public void testCollectMetrics() {
        AgentConfig config = new AgentConfig();
        config.setJvmInstanceId("test-metrics-jvm");

        SystemMetricsSnapshot snapshot = MetricsCollector.collect(config);

        assertNotNull(snapshot);
        assertNotNull(snapshot.getTimestamp());
        assertTrue(snapshot.getTimestampEpochMs() > 0);
        assertEquals("test-metrics-jvm", snapshot.getJvmId());
        assertTrue(snapshot.getHeapMaxBytes() > 0);
        assertTrue(snapshot.getHeapUsedBytes() > 0);
        assertTrue(snapshot.getLiveThreadCount() > 0);
        assertTrue(snapshot.getAvailableProcessors() > 0);

        Map<String, Object> map = snapshot.toMap();
        assertNotNull(map);
        assertTrue(map.containsKey("jvm"));
        assertTrue(map.containsKey("system"));
        assertTrue(map.containsKey("agent"));

        Map<String, Object> jvmMap = (Map<String, Object>) map.get("jvm");
        assertNotNull(jvmMap.get("heapUsagePercent"));
        assertNotNull(jvmMap.get("liveThreadCount"));

        Map<String, Object> sysMap = (Map<String, Object>) map.get("system");
        assertNotNull(sysMap.get("availableProcessors"));
    }
}
