package com.mderyol.javaagent;

import com.mderyol.javaagent.config.Argument;
import com.mderyol.javaagent.helper.ParameterHelper;
import com.mderyol.javaagent.logger.Logger;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import java.util.List;

public class Agent {
    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        premain(agentArgs, instrumentation);
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        System.out.println("Config: " + agentArgs);
        Argument argument = Argument.getInstance();
        argument.setParameters(ParameterHelper.argParser(agentArgs));
        argument.setConfig(ParameterHelper.parserConfigFile());
        instrumentation(instrumentation);
    }

    private static void instrumentation(Instrumentation instrumentation) {
        if (!Argument.getInstance().getConfig().isTimeMetricOpen()) {
            return;
        }
        System.out.println("AGENT - timing metric trace enable");
        Logger.getInstance();
        prepareAgentBuilder(instrumentation);
    }

    private static void prepareAgentBuilder(Instrumentation instrumentation) {
        Argument argument = Argument.getInstance();
        ElementMatcher.Junction<NamedElement> ignoreMatcherClass = getIgnoreMatcher(argument.getConfig().getExcludePackageNames());
        ElementMatcher.Junction<NamedElement> ignoreMatcherMethod = getIgnoreMatcher(argument.getConfig().getExcludeMethodKeywords());
        ElementMatcher.Junction<AnnotationSource> ignoreAnnotation = getIgnoreMatcherAnnotation(argument.getConfig().getExcludeAnnotations());
        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(
                        ElementMatchers.nameStartsWith(argument.getConfig().getBasePackageName())
                                .and(ElementMatchers.not(ElementMatchers.nameContains("$")))
                                .and(ElementMatchers.not(ElementMatchers.isEnum()))
                                .and(ElementMatchers.not(ElementMatchers.isInterface()))
                                .and(ElementMatchers.not(ElementMatchers.isAbstract()))
                                .and(ignoreMatcherClass)
                )
                .transform((builder, typeDefinitions, classLoader, module, protectionDomain) ->
                        builder.method(
                                ElementMatchers.not(ElementMatchers.nameContains("$"))
                                        .and(ignoreMatcherClass)
                                        .and(ignoreAnnotation)
                                )
                                .intercept(Advice.to(MethodTimingAdvice.class))
                )
                .installOn(instrumentation);
    }

    private static ElementMatcher.Junction<AnnotationSource> getIgnoreMatcherAnnotation(List<String> annotations) {
        ElementMatcher.Junction<AnnotationSource> result = null;
        for (String annotation : annotations) {
            if (result == null) {
                result = ElementMatchers.not(ElementMatchers.isAnnotatedWith(ElementMatchers.named(annotation)));
            }
            result = result.and(ElementMatchers.not(ElementMatchers.isAnnotatedWith(ElementMatchers.named(annotation))));
        }
        return result;
    }

    private static ElementMatcher.Junction<NamedElement> getIgnoreMatcher(List<String> packageNames) {
        ElementMatcher.Junction<NamedElement> result = null;
        for (String packageName : packageNames) {
            if (result == null) {
                result = ElementMatchers.not(ElementMatchers.nameStartsWith(packageName));
            }
            result = result.and(ElementMatchers.not(ElementMatchers.nameStartsWith(packageName)));
        }
        return result;
    }
}
