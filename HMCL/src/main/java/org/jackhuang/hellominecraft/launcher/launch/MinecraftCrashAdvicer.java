/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.launch;

import org.jackhuang.hellominecraft.C;

/**
 * Give the advice to solve the Minecraft crashing.
 *
 * @author hyh
 */
public final class MinecraftCrashAdvicer {

    public static String getAdvice(String trace) {
        return getAdvice(trace, false);
    }
    
    public static String getAdvice(String trace, boolean selfCrash) {
        /*if (t.getCause() instanceof UnsupportedClassVersionError) {
            return C.i18n("crash.advice.UnsupportedClassVersionError");
        } else if (t instanceof ConcurrentModificationException) {
            return C.i18n("crash.advice.ConcurrentModificationException");
        } else if (t instanceof SecurityException) {
            return C.i18n("crash.advice.SecurityException");
        } else if (t instanceof InvocationTargetException) {
            return C.i18n("crash.advice.InvocationTargetException");
        } else if (t instanceof NoSuchFieldError || (t.getCause() != null && t.getCause() instanceof NoSuchFieldException)) {
            return C.i18n("crash.advice.NoSuchFieldError");
        } else if (t instanceof NoClassDefFoundError || t instanceof ClassNotFoundException || (t.getCause() != null && t.getCause() instanceof ClassNotFoundException)) {
            return C.i18n("crash.advice.ClassNotFondException");
        }*/
        
        if (trace.contains("LWJGLException")) {
            if(trace.contains("Pixel format not accelerated"))
                return C.i18n("crash.advice.LWJGLException");
        } else if (trace.contains("UnsupportedClassVersionError")) {
            return C.i18n("crash.advice.UnsupportedClassVersionError");
        } else if (trace.contains("ConcurrentModificationException")) {
            return C.i18n("crash.advice.ConcurrentModificationException");
        } else if (trace.contains("SecurityException")) {
            return C.i18n("crash.advice.SecurityException");
        } else if (trace.contains("NoSuchFieldException") || trace.contains("NoSuchFieldError")) {
            return C.i18n("crash.advice.NoSuchFieldError");
        } else if (trace.contains("NoClassDefFoundError") || trace.contains("ClassNotFoundException")) {
            return C.i18n("crash.advice.ClassNotFondException");
        } else if (trace.contains("no lwjgl in java.library.path")) {
            return C.i18n("crash.advice.no_lwjgl");
        } else if (trace.contains("OpenGL") || trace.contains("OpenAL")) {
            return C.i18n("crash.advice.OpenGL");
        }
        return selfCrash ? C.i18n("crash.advice.no") : C.i18n("crash.advice.otherwise");
    }

}
