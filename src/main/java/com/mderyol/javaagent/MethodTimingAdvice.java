package com.mderyol.javaagent;

import net.bytebuddy.asm.Advice;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class MethodTimingAdvice {
    @Advice.OnMethodEnter
    static long onEnter(@Advice.Origin Method method) {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void onExit(@Advice.Enter long startTime, @Advice.Origin Method method) {
        if (isGetter(method) || isSetter(method) || isIgnoreMethod(method)) {
            return;
        }
        long duration = System.nanoTime() - startTime;
        long threadId = Thread.currentThread().getId();
        String transactionId = UUID.randomUUID().toString();

        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String callerService = "N/A";
        if(stackTraceElements.length > 2) {
            StackTraceElement stackTraceElement = stackTraceElements[2];
            callerService = stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName();
        }

    }

    public static boolean isGetter(Method method) {
        if (method.getParameterCount() > 0) {
            return false;
        }
        String methodName = method.getName();
        if (methodName.startsWith("get") && !void.class.equals(method.getReturnType())) {
            String fieldName = methodName.substring(3);
            return hasField(method.getDeclaringClass(), fieldName);
        } else if (methodName.startsWith("is") && (boolean.class.equals(method.getReturnType()) || Boolean.class.equals(method.getReturnType()))) {
            String fieldName = methodName.substring(2);
            return hasField(method.getDeclaringClass(), fieldName);
        }
        return false;
    }

    public static boolean isSetter(Method method) {
        if (method.getParameterCount() == 1 && method.getName().startsWith("set") && void.class.equals(method.getReturnType())) {
            String fieldName = method.getName().substring(3);
            return hasField(method.getDeclaringClass(), fieldName);
        }
        return false;
    }

    private static boolean hasField(Class<?> clazz, String fieldName) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getName().equalsIgnoreCase(fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIgnoreMethod(Method method) {
        String methodName = method.getName();
        return methodName.equals("toString")
                || methodName.equals("hashCode")
                || methodName.equals("equals")
                || methodName.equals("clone")
                || methodName.equals("equalsIgnoreCase");
    }
}
