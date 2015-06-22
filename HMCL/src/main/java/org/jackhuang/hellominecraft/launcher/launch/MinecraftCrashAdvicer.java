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
