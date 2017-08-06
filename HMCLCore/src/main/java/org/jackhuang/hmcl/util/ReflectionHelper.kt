/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.util

import java.lang.reflect.AccessibleObject
import sun.misc.Unsafe
import java.lang.reflect.Method
import java.security.PrivilegedExceptionAction
import java.security.AccessController

object ReflectionHelper {

    private lateinit var unsafe: Unsafe
    private var objectFieldOffset = 0L

    init {
        try {
            unsafe = AccessController.doPrivileged(PrivilegedExceptionAction {
                val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
                theUnsafe.isAccessible = true
                theUnsafe.get(null) as Unsafe
            })
            val overrideField = AccessibleObject::class.java.getDeclaredField("override")
            objectFieldOffset = unsafe.objectFieldOffset(overrideField)
        } catch (ex: Throwable) {
        }
    }

    private fun setAccessible(obj: AccessibleObject) =
            unsafe.putBoolean(obj, objectFieldOffset, true)

    fun <T> get(obj: Any, fieldName: String): T? =
            get(obj.javaClass, obj, fieldName)

    @Suppress("UNCHECKED_CAST")
    fun <T, S> get(cls: Class<out S>, obj: S, fieldName: String): T? =
            try {
                val method = cls.getDeclaredField(fieldName)
                setAccessible(method)
                method.get(obj) as T
            } catch (ex: Throwable) {
                null
            }

    @Suppress("UNCHECKED_CAST")
    fun <T> invoke(obj: Any, methodName: String): T? =
            try {
                val method = obj.javaClass.getDeclaredMethod(methodName)
                setAccessible(method)
                method.invoke(obj) as T?
            } catch (ex: Throwable) {
                null
            }

    fun getMethod(obj: Any, methodName: String): Method? =
            getMethod(obj.javaClass, methodName)

    fun getMethod(cls: Class<*>, methodName: String): Method? =
            try {
                cls.getDeclaredMethod(methodName).apply { ReflectionHelper.setAccessible(this) }
            } catch (ex: Throwable) {
                null
            }
}