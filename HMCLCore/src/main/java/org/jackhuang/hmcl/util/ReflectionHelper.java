/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util;

import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public final class ReflectionHelper {

    private static final Map<String, Class<?>> PRIMITIVES;

    static {
        PRIMITIVES = Lang.mapOf(
                new Pair<>("byte", Byte.class),
                new Pair<>("short", Short.class),
                new Pair<>("int", Integer.class),
                new Pair<>("long", Long.class),
                new Pair<>("char", Character.class),
                new Pair<>("float", Float.class),
                new Pair<>("double", Double.class),
                new Pair<>("boolean", Boolean.class)
        );
    }

    public static boolean isInstance(Class<?> superClass, Object obj) {
        return superClass.isInstance(obj) || PRIMITIVES.get(superClass.getName()) == obj.getClass();
    }

    public static StackTraceElement getCaller() {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        StackTraceElement caller = elements[2];
        for (int i = 3; i < elements.length; ++i) {
            if (!caller.getClassName().equals(elements[i].getClassName()))
                return elements[i];
        }
        return caller;
    }
}
