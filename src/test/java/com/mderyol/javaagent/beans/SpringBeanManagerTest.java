package com.mderyol.javaagent.beans;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SpringBeanManagerTest {

    public static class FakeSpringContext {
        public String[] getBeanDefinitionNames() {
            return new String[]{"orderService", "paymentGateway", "internalDataSource"};
        }

        public Class<?> getType(String name) {
            return switch (name) {
                case "orderService" -> FakeSpringContext.class;
                case "paymentGateway" -> String.class;
                default -> Integer.class;
            };
        }

        public boolean isSingleton(String name) {
            return true;
        }

        public String[] getAliases(String name) {
            return new String[0];
        }
    }

    @Test
    public void testWithoutContext() {
        SpringBeanManager manager = SpringBeanManager.getInstance();
        Map<String, Object> snapshot = manager.getBeansSnapshot(null, null);

        assertNotNull(snapshot);
        assertTrue(snapshot.containsKey("status"));
        assertEquals(0, snapshot.get("totalBeans"));
    }

    @Test
    public void testWithRegisteredContext() {
        SpringBeanManager manager = SpringBeanManager.getInstance();
        FakeSpringContext fakeContext = new FakeSpringContext();

        manager.registerApplicationContext(fakeContext);
        assertTrue(manager.isContextAvailable());

        Map<String, Object> snapshot = manager.getBeansSnapshot(null, null);
        assertNotNull(snapshot);
        assertEquals("SUCCESS", snapshot.get("status"));
        assertEquals(3, snapshot.get("totalBeans"));

        List<Map<String, Object>> beans = (List<Map<String, Object>>) snapshot.get("beans");
        assertEquals(3, beans.size());
        assertEquals("orderService", beans.get(0).get("beanName"));

        // Test filtering
        Map<String, Object> filtered = manager.getBeansSnapshot("payment", null);
        List<Map<String, Object>> filteredBeans = (List<Map<String, Object>>) filtered.get("beans");
        assertEquals(1, filteredBeans.size());
        assertEquals("paymentGateway", filteredBeans.get(0).get("beanName"));
    }
}
