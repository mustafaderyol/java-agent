package com.mderyol.javaagent;

import java.lang.instrument.Instrumentation;
import java.util.List;

import com.mderyol.javaagent.config.AgentConfig;
import com.mderyol.javaagent.interceptor.MethodTraceAdvice;
import com.mderyol.javaagent.interceptor.ServletTraceAdvice;
import com.mderyol.javaagent.logger.AsyncTraceLogger;
import com.mderyol.javaagent.logger.HostInfo;
import com.mderyol.javaagent.probe.ProbeServer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class Agent {

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        premain(agentArgs, instrumentation);
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        System.out.println("==================================================================");
        System.out.println("   Enterprise Java Tracing & Dynamic Probe Agent (v2.0) Starting   ");
        System.out.println("==================================================================");

        try {
            // 1. Load configuration
            AgentConfig config = AgentConfig.init(agentArgs);
            if (!config.isEnabled()) {
                System.out.println("[JavaAgent] Agent is disabled via configuration.");
                return;
            }

            // 2. Set custom JVM ID and Host metadata
            if (config.getJvmInstanceId() != null) {
                HostInfo.setCustomJvmId(config.getJvmInstanceId());
            }
            System.out.println("[JavaAgent] Node Host: " + HostInfo.getHostName());
            System.out.println("[JavaAgent] Node JVM ID: " + HostInfo.getJvmId() + " (PID: " + HostInfo.getPid() + ")");

            // 3. Initialize Asynchronous Trace Logger
            AsyncTraceLogger.init(config);
            System.out.println("[JavaAgent] AsyncTraceLogger initialized (dir: " + config.getLogDirectory() + ")");

            // 4. Initialize Periodic System & JVM Metrics Logger
            if (config.isMetricsEnabled()) {
                com.mderyol.javaagent.metrics.AsyncMetricsLogger.init(config);
                System.out.println("[JavaAgent] AsyncMetricsLogger initialized (file: " + config.getEffectiveMetricsFileName()
                        + ", interval: " + config.getMetricsIntervalSeconds() + "s)");
            }

            // 5. Optional Environment Variables & System Properties file logging
            com.mderyol.javaagent.env.EnvironmentManager.logToFileIfEnabled(config);

            // 6. Initialize Method Error Stack Trace Logger
            com.mderyol.javaagent.error.AsyncErrorLogger.init(config);
            com.mderyol.javaagent.error.ErrorLogManager.init(config);
            System.out.println("[JavaAgent] AsyncErrorLogger initialized (file: " + config.getEffectiveErrorFileName()
                    + ", default: " + (config.isErrorLoggingEnabled() ? "ENABLED" : "DISABLED") + ")");

            // 7. Start Dynamic Remote Probe & Web UI HTTP Server
            ProbeServer.start(config);

            // 6. Install Bytecode Instrumentation
            installAgent(instrumentation, config);

            System.out.println("[JavaAgent] Bytecode instrumentation successfully registered for base packages: "
                    + config.getBasePackages());

        } catch (Throwable t) {
            System.err.println("[JavaAgent] Fatal error during agent startup: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private static void installAgent(Instrumentation instrumentation, AgentConfig config) {
        ElementMatcher.Junction<NamedElement> basePackageMatcher;
        List<String> basePackages = config.getBasePackages();

        if (basePackages == null || basePackages.isEmpty() ||
                basePackages.stream().allMatch(p -> p == null || p.isBlank() || p.trim().equals("*"))) {
            // If empty: trace all packages (except exclusions)
            basePackageMatcher = ElementMatchers.any();
            System.out.println("[JavaAgent] Tracing scope: ALL PACKAGES (*) (Excluding specified libraries)");
        } else {
            basePackageMatcher = buildPrefixMatcher(basePackages);
            System.out.println("[JavaAgent] Tracing scope: " + basePackages + " (and all sub-packages)");
        }

        ElementMatcher.Junction<NamedElement> excludePackageMatcher = buildExcludeMatcher(config.getExcludePackages());
        ElementMatcher.Junction<NamedElement> excludeMethodMatcher = buildExcludeMethodMatcher(config.getExcludeMethodKeywords());

        new AgentBuilder.Default()
                .ignore(
                        nameStartsWith("java.")
                                .or(nameStartsWith("javax."))
                                .or(nameStartsWith("jdk."))
                                .or(nameStartsWith("sun."))
                                .or(nameStartsWith("com.mderyol.javaagent"))
                                .or(nameStartsWith("net.bytebuddy"))
                )
                .type(
                        basePackageMatcher
                                .and(excludePackageMatcher)
                                .and(not(isInterface()))
                                .and(not(isAnnotation()))
                                .and(not(isEnum()))
                                .and(not(isAbstract()))
                                .and(not(isSynthetic()))
                )
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                    // 1. Method Tracing Advice (Call tree, latency, dynamic probe)
                    builder = builder.method(
                            not(isConstructor())
                                    .and(not(isStatic()))
                                    .and(not(isGetter()))
                                    .and(not(isSetter()))
                                    .and(not(isSynthetic()))
                                    .and(excludeMethodMatcher)
                    ).intercept(Advice.to(MethodTraceAdvice.class));

                    // 2. HTTP/Servlet Transaction Interceptor (Captures distributed TransactionId at controller layer)
                    builder = builder.method(
                            isAnnotatedWith(nameContainsIgnoreCase("Mapping"))
                                    .or(named("service").or(named("doFilter")))
                    ).intercept(Advice.to(ServletTraceAdvice.class));

                    return builder;
                })
                .installOn(instrumentation);

        // 3. Optional Spring Bean Discovery Interception (captures ApplicationContext on finishRefresh)
        if (config.isCaptureSpringBeans()) {
            new AgentBuilder.Default()
                    .type(hasSuperType(named("org.springframework.context.ApplicationContext"))
                            .or(nameContains("AbstractApplicationContext")))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                            builder.method(named("finishRefresh").or(named("refresh")))
                                    .intercept(Advice.to(com.mderyol.javaagent.beans.SpringContextAdvice.class)))
                    .installOn(instrumentation);
        }
    }

    private static ElementMatcher.Junction<NamedElement> buildPrefixMatcher(List<String> prefixes) {
        ElementMatcher.Junction<NamedElement> matcher = ElementMatchers.none();
        for (String p : prefixes) {
            String clean = cleanWildcard(p);
            if (!clean.isEmpty()) {
                matcher = matcher.or(nameStartsWith(clean));
            }
        }
        return matcher;
    }

    private static ElementMatcher.Junction<NamedElement> buildExcludeMatcher(List<String> excludes) {
        ElementMatcher.Junction<NamedElement> matcher = ElementMatchers.any();
        for (String exc : excludes) {
            String clean = cleanWildcard(exc);
            if (!clean.isEmpty()) {
                matcher = matcher.and(not(nameStartsWith(clean)));
            }
        }
        return matcher;
    }

    private static String cleanWildcard(String pkg) {
        if (pkg == null) return "";
        String p = pkg.trim();
        if (p.endsWith(".*")) {
            p = p.substring(0, p.length() - 2);
        } else if (p.endsWith("*")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static ElementMatcher.Junction<NamedElement> buildExcludeMethodMatcher(List<String> methodNames) {
        ElementMatcher.Junction<NamedElement> matcher = ElementMatchers.any();
        for (String m : methodNames) {
            matcher = matcher.and(not(named(m.trim())));
        }
        return matcher;
    }
}
