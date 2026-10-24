package com.mderyol.javaagent.config;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AgentConfigTest {

    @Test
    public void testConfigInitializationAndPort() {
        AgentConfig config = AgentConfig.init("probePort=9999,basePackages=com.test.*");
        assertEquals(9999, config.getProbePort());
        assertTrue(config.getBasePackages().contains("com.test.*"));
    }

    @Test
    public void testEmptyBasePackagesAllowsTraceAll() {
        AgentConfig config = AgentConfig.init("basePackages=*");
        assertTrue(config.getBasePackages().isEmpty() || config.getBasePackages().contains("*"));
    }

    @Test
    public void testExcludePackagesList() {
        AgentConfig config = AgentConfig.init("excludePackages=org.springframework.*;io.netty.*");
        assertEquals(2, config.getExcludePackages().size());
        assertTrue(config.getExcludePackages().contains("org.springframework.*"));
        assertTrue(config.getExcludePackages().contains("io.netty.*"));
    }
}
