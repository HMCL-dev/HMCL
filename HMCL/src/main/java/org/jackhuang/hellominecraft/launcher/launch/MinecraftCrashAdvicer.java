/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.launch;

import java.util.ConcurrentModificationException;
import org.jackhuang.hellominecraft.C;

/**
 * Give the advice to solve the Minecraft crashing.
 *
 * @author hyh
 */
public final class MinecraftCrashAdvicer {

    public static String getAdvice(Throwable t) {
        return getAdvice(t, false);
    }
    
    public static String getAdvice(Throwable t, boolean selfCrash) {
        if (t.getCause() instanceof UnsupportedClassVersionError) {
            return C.i18n("crash.advice.UnsupportedClassVersionError");
        } else if (t instanceof ConcurrentModificationException) {
            return C.i18n("crash.advice.ConcurrentModificationException");
        } else if (t instanceof SecurityException) {
            return C.i18n("crash.advice.SecurityException");
        } else if (t instanceof NoSuchFieldError || (t.getCause() != null && t.getCause() instanceof NoSuchFieldException)) {
            return C.i18n("crash.advice.NoSuchFieldError");
        } else if (t instanceof NoClassDefFoundError || (t.getCause() != null && t.getCause() instanceof ClassNotFoundException)) {
            return C.i18n("crash.advice.ClassNotFondException");
        }

        if (t.getMessage() != null) {
            if (t.getMessage().contains("OpenGL") || t.getMessage().contains("OpenAL")) {
                return C.i18n("crash.advice.OpenGL");
            }
        }
        if (t.getCause() != null && t.getCause().getMessage() != null) {
            if (t.getCause().getMessage().contains("no lwjgl in java.library.path")) {
                return C.i18n("crash.advice.no_lwjgl");
            }
        }
        return selfCrash ? C.i18n("crash.advice.no") : C.i18n("crash.advice.otherwise");
    }

}
