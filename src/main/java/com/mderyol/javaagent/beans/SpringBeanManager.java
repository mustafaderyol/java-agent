package com.mderyol.javaagent.beans;

import java.io.BufferedWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mderyol.javaagent.config.AgentConfig;
import com.mderyol.javaagent.logger.HostInfo;

/**
 * Non-invasive Spring Bean discovery and inspection manager.
 * Operates purely via reflection with zero hard compile-time dependencies on Spring.
 */
public class SpringBeanManager {

    private static final SpringBeanManager INSTANCE = new SpringBeanManager();
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final AtomicReference<Object> applicationContextRef = new AtomicReference<>(null);

    private SpringBeanManager() {}

    public static SpringBeanManager getInstance() {
        return INSTANCE;
    }

    public void registerApplicationContext(Object context) {
        if (context == null) return;
        this.applicationContextRef.set(context);

        System.out.println("[JavaAgent] Spring ApplicationContext captured: " + context.getClass().getName());

        AgentConfig config = AgentConfig.getInstance();
        if (config.isLogBeansOnStartup()) {
            logBeansToFile(config);
        }
    }

    public boolean isContextAvailable() {
        return applicationContextRef.get() != null;
    }

    public Object getApplicationContext() {
        return applicationContextRef.get();
    }

    /**
     * Inspects registered Spring beans via reflection.
     */
    public Map<String, Object> getBeansSnapshot(String nameFilter, String packageFilter) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());
        result.put("host", HostInfo.getHostName());
        result.put("jvmId", HostInfo.getJvmId());

        Object context = applicationContextRef.get();
        if (context == null) {
            result.put("status", "NO_SPRING_CONTEXT_CAPTURED");
            result.put("message", "Spring ApplicationContext has not been initialized or captured yet.");
            result.put("totalBeans", 0);
            result.put("beans", Collections.emptyList());
            return result;
        }

        try {
            Class<?> ctxClass = context.getClass();
            Method getNamesMethod = findMethod(ctxClass, "getBeanDefinitionNames");
            Method getTypeMethod = findMethod(ctxClass, "getType", String.class);
            Method isSingletonMethod = findMethod(ctxClass, "isSingleton", String.class);
            Method getAliasesMethod = findMethod(ctxClass, "getAliases", String.class);

            if (getNamesMethod == null) {
                result.put("status", "ERROR");
                result.put("message", "getBeanDefinitionNames method not found on context: " + ctxClass.getName());
                return result;
            }

            String[] beanNames = (String[]) getNamesMethod.invoke(context);
            List<Map<String, Object>> beanList = new ArrayList<>();

            for (String name : beanNames) {
                if (nameFilter != null && !nameFilter.isBlank() && !name.toLowerCase(Locale.ROOT).contains(nameFilter.toLowerCase(Locale.ROOT))) {
                    continue;
                }

                Class<?> type = null;
                if (getTypeMethod != null) {
                    try {
                        type = (Class<?>) getTypeMethod.invoke(context, name);
                    } catch (Throwable ignored) {}
                }

                String className = type != null ? type.getName() : "Unknown";
                String packageName = type != null && type.getPackage() != null ? type.getPackage().getName() : "";

                if (packageFilter != null && !packageFilter.isBlank() && !packageName.toLowerCase(Locale.ROOT).contains(packageFilter.toLowerCase(Locale.ROOT))) {
                    continue;
                }

                boolean isSingleton = true;
                if (isSingletonMethod != null) {
                    try {
                        isSingleton = (Boolean) isSingletonMethod.invoke(context, name);
                    } catch (Throwable ignored) {}
                }

                String[] aliases = new String[0];
                if (getAliasesMethod != null) {
                    try {
                        aliases = (String[]) getAliasesMethod.invoke(context, name);
                    } catch (Throwable ignored) {}
                }

                Map<String, Object> beanInfo = new LinkedHashMap<>();
                beanInfo.put("beanName", name);
                beanInfo.put("className", className);
                beanInfo.put("packageName", packageName);
                beanInfo.put("isSingleton", isSingleton);
                if (aliases != null && aliases.length > 0) {
                    beanInfo.put("aliases", Arrays.asList(aliases));
                }

                // Collect accessible public methods on business beans for easy probe attachment
                if (type != null && !className.startsWith("org.springframework.") && !className.startsWith("java.")) {
                    List<String> methods = new ArrayList<>();
                    for (Method m : type.getDeclaredMethods()) {
                        if (java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                            methods.add(m.getName());
                        }
                    }
                    if (!methods.isEmpty()) {
                        beanInfo.put("publicMethods", methods);
                    }
                }

                beanList.add(beanInfo);
            }

            result.put("status", "SUCCESS");
            result.put("contextClass", ctxClass.getName());
            result.put("totalBeans", beanNames.length);
            result.put("filteredBeansCount", beanList.size());
            result.put("beans", beanList);

        } catch (Throwable t) {
            result.put("status", "ERROR");
            result.put("error", t.getMessage());
        }

        return result;
    }

    public void logBeansToFile(AgentConfig config) {
        try {
            Path logDir = Paths.get(config.getLogDirectory());
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }

            String fileName = "beans-" + config.getEffectiveJvmId() + ".log";
            Path targetFile = logDir.resolve(fileName);

            Map<String, Object> snapshot = getBeansSnapshot(null, null);
            String json = MAPPER.writeValueAsString(snapshot);

            try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write(json);
            }
            System.out.println("[JavaAgent] Spring beans successfully logged to: " + targetFile.toAbsolutePath());
        } catch (Throwable t) {
            System.err.println("[JavaAgent] Failed to log Spring beans: " + t.getMessage());
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            try {
                Method m = clazz.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
