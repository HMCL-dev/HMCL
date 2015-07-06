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
 * @author huangyuhui
 */
public final class MinecraftCrashAdvicer {

    public static String getAdvice(String trace) {
        return getAdvice(trace, false);
    }
    
    public static String getAdvice(String trace, boolean selfCrash) {
        trace = trace.toLowerCase();
        if(trace.contains("pixel format not accelerated")) {
            return C.i18n("crash.advice.LWJGLException");
        } else if (trace.contains("unsupportedclassversionrrror")) {
            return C.i18n("crash.advice.UnsupportedClassVersionError");
        } else if (trace.contains("concurrentmodificationexception")) {
            return C.i18n("crash.advice.ConcurrentModificationException");
        } else if (trace.contains("securityexception")) {
            return C.i18n("crash.advice.SecurityException");
        } else if (trace.contains("nosuchfieldexception") || trace.contains("nosuchfielderror")) {
            return C.i18n("crash.advice.NoSuchFieldError");
        } else if (trace.contains("outofmemory") || trace.contains("out of memory")) {
            return C.i18n("crash.advice.OutOfMemoryError");
        } else if (trace.contains("noclassdeffounderror") || trace.contains("classnotfoundexception")) {
            return C.i18n("crash.advice.ClassNotFoundException");
        } else if (trace.contains("no lwjgl in java.library.path")) {
            return C.i18n("crash.advice.no_lwjgl");
        } else if (trace.contains("opengl") || trace.contains("openal")) {
            return C.i18n("crash.advice.OpenGL");
        }
        return C.i18n(selfCrash ? "crash.advice.no" : "crash.advice.otherwise");
    }

}
