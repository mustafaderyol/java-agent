package com.mderyol.javaagent.probe;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mderyol.javaagent.config.AgentConfig;

public class DynamicProbeManager {
    private static final DynamicProbeManager INSTANCE = new DynamicProbeManager();

    private final Map<String, ProbeRule> activeRules = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    private DynamicProbeManager() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static DynamicProbeManager getInstance() {
        return INSTANCE;
    }

    public String registerProbe(String className, String methodName, int durationSeconds, int maxCaptures) {
        if (className == null || className.isBlank()) {
            return "Error: className cannot be empty";
        }
        ProbeRule rule = new ProbeRule(className.trim(), methodName != null ? methodName.trim() : null, durationSeconds, maxCaptures);
        String key = buildKey(rule.getClassName(), rule.getMethodName());
        activeRules.put(key, rule);
        System.out.println("[JavaAgent] Probe registered for " + key + " (duration: " + durationSeconds + "s, max: " + maxCaptures + ")");
        return "SUCCESS: Probe active for " + key + " (expires in " + durationSeconds + "s)";
    }

    public boolean removeProbe(String className, String methodName) {
        String key = buildKey(className, methodName);
        ProbeRule removed = activeRules.remove(key);
        return removed != null;
    }

    public void clearAll() {
        activeRules.clear();
    }

    public Map<String, ProbeRule> getActiveRules() {
        // Evict expired rules
        cleanExpiredRules();
        return Collections.unmodifiableMap(activeRules);
    }

    /**
     * O(1) probe evaluation check: is there an active rule matching this method?
     */
    public ProbeRule getActiveRule(String className, String methodName) {
        if (activeRules.isEmpty()) return null;

        String specificKey = buildKey(className, methodName);
        ProbeRule rule = activeRules.get(specificKey);
        if (rule != null) {
            if (rule.isExpired()) {
                activeRules.remove(specificKey);
                return null;
            }
            return rule;
        }

        // 2. Class-level probe check
        String wildcardKey = buildKey(className, null);
        rule = activeRules.get(wildcardKey);
        if (rule != null) {
            if (rule.isExpired()) {
                activeRules.remove(wildcardKey);
                return null;
            }
            return rule;
        }

        // 3. Pattern / Wildcard rules (e.g. com.mycompany.service.*)
        for (Map.Entry<String, ProbeRule> entry : activeRules.entrySet()) {
            ProbeRule candidate = entry.getValue();
            if (candidate.isExpired()) {
                activeRules.remove(entry.getKey());
                continue;
            }
            if (matchesRule(candidate, className, methodName)) {
                return candidate;
            }
        }

        return null;
    }

    private boolean matchesRule(ProbeRule rule, String targetClass, String targetMethod) {
        String ruleClass = rule.getClassName();
        String ruleMethod = rule.getMethodName();

        boolean classMatches;
        if (ruleClass.equals("*")) {
            classMatches = true;
        } else if (ruleClass.endsWith("*")) {
            String prefix = ruleClass.substring(0, ruleClass.length() - 1);
            classMatches = targetClass.startsWith(prefix);
        } else {
            classMatches = ruleClass.equals(targetClass);
        }

        if (!classMatches) return false;

        return ruleMethod == null || ruleMethod.equals("*") || ruleMethod.equals(targetMethod);
    }

    /**
     * Safe Request/Response payload serialization with size truncation protection.
     */
    public String serializePayload(Object data) {
        if (data == null) return null;
        try {
            String json = objectMapper.writeValueAsString(data);
            int maxLen = AgentConfig.getInstance().getMaxPayloadLength();
            if (json.length() > maxLen) {
                return json.substring(0, maxLen) + "...[TRUNCATED]";
            }
            return json;
        } catch (Throwable t) {
            return "{\"serialization_error\": \"" + t.getClass().getSimpleName() + ": " + t.getMessage() + "\"}";
        }
    }

    private void cleanExpiredRules() {
        activeRules.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private String buildKey(String className, String methodName) {
        if (methodName == null || methodName.isBlank() || methodName.equals("*")) {
            return className + "#*";
        }
        return className + "#" + methodName;
    }
}
