package com.mderyol.javaagent.beans;

import net.bytebuddy.asm.Advice;

/**
 * ByteBuddy advice that intercepts Spring ApplicationContext initialization
 * when finishRefresh() executes.
 */
public class SpringContextAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onFinishRefresh(@Advice.This Object context) {
        try {
            SpringBeanManager.getInstance().registerApplicationContext(context);
        } catch (Throwable ignored) {}
    }
}
