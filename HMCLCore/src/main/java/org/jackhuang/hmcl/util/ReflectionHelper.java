/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 *
 * @author huangyuhui
 */
public final class ReflectionHelper {
    private ReflectionHelper() {
    }

    private static Method accessible0;

    static {
        try {
            accessible0 = AccessibleObject.class.getDeclaredMethod("setAccessible0", boolean.class);
            accessible0.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }

    public static void setAccessible(AccessibleObject obj) throws InvocationTargetException, IllegalAccessException {
        accessible0.invoke(obj, true);
    }

    /**
     * Get caller, this method is caller sensitive.
     *
     * @param packageFilter returns false if we consider the given package is internal calls, not the caller
     * @return the caller, method name, source file, line number
     */
    public static StackTraceElement getCaller(Predicate<String> packageFilter) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        // element[0] is Thread.currentThread().getStackTrace()
        // element[1] is ReflectionHelper.getCaller(packageFilter)
        // so element[2] is caller of this method.
        StackTraceElement caller = elements[2];
        for (int i = 3; i < elements.length; ++i) {
            if (packageFilter.test(StringUtils.substringBeforeLast(elements[i].getClassName(), '.')) &&
                    !caller.getClassName().equals(elements[i].getClassName()))
                return elements[i];
        }
        return caller;
    }
}
