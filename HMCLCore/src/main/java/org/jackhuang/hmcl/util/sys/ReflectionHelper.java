/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.util.sys;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import sun.misc.Unsafe;

/**
 *
 * @author huang
 */
public class ReflectionHelper {
    
    private static Unsafe unsafe = null;
    private static long objectFieldOffset;
    
    static {
        try {
            unsafe = AccessController.doPrivileged(new PrivilegedExceptionAction<Unsafe> () {
                @Override
                public Unsafe run() throws Exception {
                    Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                    theUnsafe.setAccessible(true);
                    return (Unsafe) theUnsafe.get(null);
                }
            });
            Field overrideField = AccessibleObject.class.getDeclaredField("override");
            objectFieldOffset = unsafe.objectFieldOffset(overrideField);
        } catch (Throwable ex) {
        }
    }
    
    private static void setAccessible(AccessibleObject obj) {
        unsafe.putBoolean(obj, objectFieldOffset, true);
    }
    
    public static <T> T get(Object obj, String fieldName) {
        try {
            Method method = obj.getClass().getDeclaredMethod(fieldName);
            setAccessible(method);
            return (T) method.invoke(obj);
        } catch (Throwable ex) {
            return null;
        }
    }
}
