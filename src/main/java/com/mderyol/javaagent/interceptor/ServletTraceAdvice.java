package com.mderyol.javaagent.interceptor;

import java.lang.reflect.Method;
import java.util.UUID;
import net.bytebuddy.asm.Advice;
import com.mderyol.javaagent.context.TraceContext;

public class ServletTraceAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.AllArguments Object[] args) {
        String existingTxId = null;

        // Argümanlar arasında HttpServletRequest / ServletRequest var mı kontrol et
        if (args != null) {
            for (Object arg : args) {
                if (arg != null) {
                    String className = arg.getClass().getName();
                    if (className.contains("ServletRequest") || className.contains("HttpServletRequest")) {
                        existingTxId = extractHeader(arg, "X-Transaction-Id");
                        if (existingTxId == null) {
                            existingTxId = extractHeader(arg, "traceparent");
                        }
                        if (existingTxId != null) {
                            break;
                        }
                    }
                }
            }
        }

        if (existingTxId == null || existingTxId.isBlank()) {
            existingTxId = UUID.randomUUID().toString();
        }

        TraceContext.initTransaction(existingTxId);
    }

    private static String extractHeader(Object requestObj, String headerName) {
        try {
            Method getHeaderMethod = requestObj.getClass().getMethod("getHeader", String.class);
            Object result = getHeaderMethod.invoke(requestObj, headerName);
            return result != null ? result.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
