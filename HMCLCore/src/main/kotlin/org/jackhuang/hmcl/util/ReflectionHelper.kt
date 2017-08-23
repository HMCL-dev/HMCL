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

import sun.misc.Unsafe
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.security.AccessController
import java.security.PrivilegedExceptionAction

private val unsafe: Unsafe = AccessController.doPrivileged(PrivilegedExceptionAction {
    val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
    theUnsafe.isAccessible = true
    theUnsafe.get(null) as Unsafe
})
private val objectFieldOffset = unsafe.objectFieldOffset(
        AccessibleObject::class.java.getDeclaredField("override"))

private fun AccessibleObject.setAccessibleForcibly() =
        unsafe.putBoolean(this, objectFieldOffset, true)

fun getMethod(obj: Any, methodName: String): Method? =
        getMethod(obj.javaClass, methodName)

fun getMethod(cls: Class<*>, methodName: String): Method? =
        try {
            cls.getDeclaredMethod(methodName).apply { setAccessibleForcibly() }
        } catch (ex: Throwable) {
            null
        }

/**
 * Call a field, method or constructor by reflection.
 *
 * @param name the field or method name of [clazz], "new" if you are looking for a constructor.
 * @param args the arguments of the method, empty if you are looking for a field or non-argument method.
 */
fun Any.call(name: String, vararg args: Any?): Any? {
    @Suppress("UNCHECKED_CAST")
    return javaClass.call(name, this, *args)
}

/**
 * Call a constructor by reflection.
 *
 * @param args the arguments of the method, empty if you are looking for a field or non-argument method.
 */
fun Class<*>.construct(vararg args: Any?) = call("new", null, *args)

/**
 * Call a field, method or constructor by reflection.
 *
 * @param name the field or method name of [clazz], "new" if you are looking for a constructor.
 * @param obj null for constructors or static/object methods/fields.
 * @param args the arguments of the method, empty if you are looking for a field or non-argument method.
 */
fun Class<*>.call(name: String, obj: Any? = null, vararg args: Any?): Any? {
    try {
        if (args.isEmpty())
            try {
                return getDeclaredField(name).get(obj)
            } catch(ignored: NoSuchFieldException) {
            }
        if (name == "new")
            declaredConstructors.forEach {
                if (checkParameter(it, *args)) return it.newInstance(*args)
            }
        else
            return forMethod(name, *args)!!.invoke(obj, *args)
        throw RuntimeException()
    } catch(e: Exception) {
        throw IllegalArgumentException("Cannot find `$name` in Class `${this.name}`, please check your code.", e)
    }
}

fun Class<*>.forMethod(name: String, vararg args: Any?): Method? =
        declaredMethods.filter { it.name == name }.filter { checkParameter(it, *args) }.firstOrNull()

fun checkParameter(exec: Executable, vararg args: Any?): Boolean {
    val cArgs = exec.parameterTypes
    if (args.size == cArgs.size) {
        for (i in 0 until args.size) {
            val arg = args[i]
            // primitive variable cannot be null
            if (if (arg != null) !isInstance(cArgs[i], arg) else cArgs[i].isPrimitive)
                return false
        }
        exec.setAccessibleForcibly()
        return true
    } else
        return false
}

fun isInstance(superClass: Class<*>, obj: Any): Boolean {
    if (superClass.isInstance(obj)) return true
    else if (PRIMITIVES[superClass.name] == obj.javaClass) return true
    return false
}

fun isInstance(superClass: Class<*>, clazz: Class<*>): Boolean {
    for (i in clazz.interfaces)
        if (isInstance(superClass, i) || PRIMITIVES[superClass.name] == clazz)
            return true
    return isSubClass(superClass, clazz)
}

fun isSubClass(superClass: Class<*>, clazz: Class<*>): Boolean {
    var clz: Class<*>? = clazz
    do {
        if (superClass == clz) return true
        clz = clz?.superclass
    } while (clz != null)
    return false
}

fun <T> Class<T>.objectInstance() = call("INSTANCE")

val PRIMITIVES = mapOf(
        "byte" to java.lang.Byte::class.java,
        "short" to java.lang.Short::class.java,
        "int" to java.lang.Integer::class.java,
        "long" to java.lang.Long::class.java,
        "char" to java.lang.Character::class.java,
        "float" to java.lang.Float::class.java,
        "double" to java.lang.Double::class.java,
        "boolean" to java.lang.Boolean::class.java
)
