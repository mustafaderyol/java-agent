package com.mderyol.javaagent.env;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EnvironmentManagerTest {

    @Test
    public void testMaskSensitiveKeys() {
        assertTrue(EnvironmentManager.isSensitive("DB_PASSWORD"));
        assertTrue(EnvironmentManager.isSensitive("API_SECRET_KEY"));
        assertTrue(EnvironmentManager.isSensitive("JWT_TOKEN"));
        assertFalse(EnvironmentManager.isSensitive("JAVA_HOME"));
        assertFalse(EnvironmentManager.isSensitive("USER"));

        String masked = EnvironmentManager.maskValue("my-super-secret-12345");
        assertEquals('m', masked.charAt(0));
        assertEquals('5', masked.charAt(masked.length() - 1));
        assertTrue(masked.contains("******"));
    }

    @Test
    public void testGetCombinedSnapshot() {
        Map<String, Object> snapshot = EnvironmentManager.getCombinedSnapshot(true, List.of("*"));
        assertNotNull(snapshot);
        assertTrue(snapshot.containsKey("environmentVariables"));
        assertTrue(snapshot.containsKey("systemProperties"));
        assertTrue(snapshot.containsKey("host"));
        assertTrue(snapshot.containsKey("jvmId"));

        Map<String, String> env = (Map<String, String>) snapshot.get("environmentVariables");
        assertNotNull(env);
    }
}
