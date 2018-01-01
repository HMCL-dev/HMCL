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

import javafx.beans.Observable
import javafx.beans.binding.*
import javafx.beans.property.*
import javafx.beans.value.*
import javafx.collections.ObservableList
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.Callable
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

fun <T> property(value: T? = null) = PropertyDelegate(SimpleObjectProperty<T>(value))
fun <T> property(block: () -> Property<T>) = PropertyDelegate(block())

class PropertyDelegate<T>(val fxProperty: Property<T>) : ReadWriteProperty<Any, T> {

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        return fxProperty.value
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        fxProperty.value = value
    }

}

fun <T> Any.getProperty(prop: KMutableProperty1<*, T>): ObjectProperty<T> {
    // avoid kotlin-reflect dependency
    val field = javaClass.findFieldByName("${prop.name}\$delegate")
            ?: throw IllegalArgumentException("No delegate field then name '${prop.name}' found")

    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val delegate = field.get(this) as PropertyDelegate<T>
    return delegate.fxProperty as ObjectProperty<T>
}

fun Class<*>.findFieldByName(name: String): Field? {
    val field = (declaredFields + fields).find { it.name == name }
    if (field != null) return field
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    if (superclass == java.lang.Object::class.java) return null
    return superclass.findFieldByName(name)
}

fun Class<*>.findMethodByName(name: String): Method? {
    val method = (declaredMethods + methods).find { it.name == name }
    if (method != null) return method
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    if (superclass == java.lang.Object::class.java) return null
    return superclass.findMethodByName(name)
}

/**
 * Convert an owner instance and a corresponding property reference into an observable
 */
fun <S, T> S.observable(prop: KMutableProperty1<S, T>) = observable(this, prop)

/**
 * Convert an owner instance and a corresponding property reference into an observable
 */
@JvmName("observableFromMutableProperty")
fun <S, T> observable(owner: S, prop: KMutableProperty1<S, T>): ObjectProperty<T> {
    return object : SimpleObjectProperty<T>(owner, prop.name) {
        override fun get() = prop.get(owner)
        override fun set(v: T) = prop.set(owner, v)
    }
}

/**
 * Convert an owner instance and a corresponding property reference into a readonly observable
 */
fun <S, T> observable(owner: S, prop: KProperty1<S, T>): ReadOnlyObjectProperty<T> {
    return object : ReadOnlyObjectWrapper<T>(owner, prop.name) {
        override fun get() = prop.get(owner)
    }
}

open class PojoProperty<T>(bean: Any, propName: String) : SimpleObjectProperty<T>(bean, propName) {
    fun refresh() {
        fireValueChangedEvent()
    }
}

enum class SingleAssignThreadSafetyMode {
    SYNCHRONIZED,
    NONE
}

fun <T> singleAssign(threadSafeyMode: SingleAssignThreadSafetyMode = SingleAssignThreadSafetyMode.SYNCHRONIZED): SingleAssign<T> =
        if (threadSafeyMode.equals(SingleAssignThreadSafetyMode.SYNCHRONIZED)) SynchronizedSingleAssign<T>() else UnsynchronizedSingleAssign<T>()

private object UNINITIALIZED_VALUE

interface SingleAssign<T> {
    fun isInitialized(): Boolean
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
}

private class SynchronizedSingleAssign<T> : SingleAssign<T> {

    @Volatile
    private var initialized = false

    @Volatile
    private var _value: Any? = UNINITIALIZED_VALUE

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!initialized)
            throw Exception("Value has not been assigned yet!")
        @Suppress("UNCHECKED_CAST")
        return _value as T
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        synchronized(this) {
            if (initialized) {
                throw Exception("Value has already been assigned!")
            }
            _value = value
            initialized = true
        }
    }

    override fun isInitialized() = initialized
}

private class UnsynchronizedSingleAssign<T> : SingleAssign<T> {

    private var initialized = false
    private var _value: Any? = UNINITIALIZED_VALUE

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!initialized)
            throw Exception("Value has not been assigned yet!")
        @Suppress("UNCHECKED_CAST")
        return _value as T
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (initialized) {
            throw Exception("Value has already been assigned!")
        }
        _value = value
        initialized = true
    }

    override fun isInitialized() = initialized
}

/**
 * Binds this property to an observable, automatically unbinding it before if already bound.
 */
fun <T> Property<T>.cleanBind(observable: ObservableValue<T>) {
    unbind()
    bind(observable)
}

operator fun <T> ObservableValue<T>.getValue(thisRef: Any, property: KProperty<*>) = value
operator fun <T> Property<T?>.setValue(thisRef: Any, property: KProperty<*>, value: T?) = setValue(value)

operator fun ObservableDoubleValue.getValue(thisRef: Any, property: KProperty<*>) = get()
operator fun DoubleProperty.setValue(thisRef: Any, property: KProperty<*>, value: Double) = set(value)

operator fun ObservableFloatValue.getValue(thisRef: Any, property: KProperty<*>) = get()
operator fun FloatProperty.setValue(thisRef: Any, property: KProperty<*>, value: Float) = set(value)

operator fun ObservableLongValue.getValue(thisRef: Any, property: KProperty<*>) = get()
operator fun LongProperty.setValue(thisRef: Any, property: KProperty<*>, value: Long) = set(value)

operator fun ObservableIntegerValue.getValue(thisRef: Any, property: KProperty<*>) = get()
operator fun IntegerProperty.setValue(thisRef: Any, property: KProperty<*>, value: Int) = set(value)

operator fun ObservableBooleanValue.getValue(thisRef: Any, property: KProperty<*>) = get()
operator fun BooleanProperty.setValue(thisRef: Any, property: KProperty<*>, value: Boolean) = set(value)

operator fun DoubleExpression.plus(other: Number): DoubleBinding = add(other.toDouble())
operator fun DoubleExpression.plus(other: ObservableNumberValue): DoubleBinding = add(other)

operator fun DoubleProperty.plusAssign(other: Number) { value += other.toDouble() }
operator fun DoubleProperty.plusAssign(other: ObservableNumberValue) { value += other.doubleValue() }

operator fun DoubleProperty.inc(): DoubleProperty {
    value++
    return this
}

operator fun DoubleExpression.minus(other: Number): DoubleBinding = subtract(other.toDouble())
operator fun DoubleExpression.minus(other: ObservableNumberValue): DoubleBinding = subtract(other)

operator fun DoubleProperty.minusAssign(other: Number) { value -= other.toDouble() }
operator fun DoubleProperty.minusAssign(other: ObservableNumberValue) { value -= other.doubleValue() }

operator fun DoubleExpression.unaryMinus(): DoubleBinding = negate()


operator fun DoubleProperty.dec(): DoubleProperty {
    value--
    return this
}

operator fun DoubleExpression.times(other: Number): DoubleBinding = multiply(other.toDouble())
operator fun DoubleExpression.times(other: ObservableNumberValue): DoubleBinding = multiply(other)

operator fun DoubleProperty.timesAssign(other: Number) { value *= other.toDouble() }
operator fun DoubleProperty.timesAssign(other: ObservableNumberValue) { value *= other.doubleValue() }

operator fun DoubleExpression.div(other: Number): DoubleBinding = divide(other.toDouble())
operator fun DoubleExpression.div(other: ObservableNumberValue): DoubleBinding = divide(other)

operator fun DoubleProperty.divAssign(other: Number) { value /= other.toDouble() }
operator fun DoubleProperty.divAssign(other: ObservableNumberValue) { value /= other.doubleValue() }


operator fun DoubleExpression.rem(other: Number): DoubleBinding = doubleBinding(this) { get() % other.toDouble() }
operator fun DoubleExpression.rem(other: ObservableNumberValue): DoubleBinding = doubleBinding(this, other) { get() % other.doubleValue() }

operator fun DoubleProperty.remAssign(other: Number) { value %= other.toDouble() }
operator fun DoubleProperty.remAssign(other: ObservableNumberValue) { value %= other.doubleValue() }

operator fun ObservableDoubleValue.compareTo(other: Number): Int {
    if (get() > other.toDouble())
        return 1
    else if (get() < other.toDouble())
        return -1
    else
        return 0
}

operator fun ObservableDoubleValue.compareTo(other: ObservableNumberValue): Int {
    if (get() > other.doubleValue())
        return 1
    else if (get() < other.doubleValue())
        return -1
    else
        return 0
}

operator fun FloatExpression.plus(other: Number): FloatBinding = add(other.toFloat())
operator fun FloatExpression.plus(other: Double): DoubleBinding = add(other)
operator fun FloatExpression.plus(other: ObservableNumberValue): FloatBinding = add(other) as FloatBinding
operator fun FloatExpression.plus(other: ObservableDoubleValue): DoubleBinding = add(other) as DoubleBinding

operator fun FloatProperty.plusAssign(other: Number) { value += other.toFloat() }
operator fun FloatProperty.plusAssign(other: ObservableNumberValue) { value += other.floatValue() }

operator fun FloatProperty.inc(): FloatProperty {
    value++
    return this
}

operator fun FloatExpression.minus(other: Number): FloatBinding = subtract(other.toFloat())
operator fun FloatExpression.minus(other: Double): DoubleBinding = subtract(other)
operator fun FloatExpression.minus(other: ObservableNumberValue): FloatBinding = subtract(other) as FloatBinding
operator fun FloatExpression.minus(other: ObservableDoubleValue): DoubleBinding = subtract(other) as DoubleBinding

operator fun FloatProperty.minusAssign(other: Number) { value -= other.toFloat() }
operator fun FloatProperty.minusAssign(other: ObservableNumberValue) { value -= other.floatValue() }

operator fun FloatExpression.unaryMinus(): FloatBinding = negate()

operator fun FloatProperty.dec(): FloatProperty {
    value--
    return this
}

operator fun FloatExpression.times(other: Number): FloatBinding = multiply(other.toFloat())
operator fun FloatExpression.times(other: Double): DoubleBinding = multiply(other)
operator fun FloatExpression.times(other: ObservableNumberValue): FloatBinding = multiply(other) as FloatBinding
operator fun FloatExpression.times(other: ObservableDoubleValue): DoubleBinding = multiply(other) as DoubleBinding

operator fun FloatProperty.timesAssign(other: Number) { value *= other.toFloat() }
operator fun FloatProperty.timesAssign(other: ObservableNumberValue) { value *= other.floatValue() }


operator fun FloatExpression.div(other: Number): FloatBinding = divide(other.toFloat())
operator fun FloatExpression.div(other: Double): DoubleBinding = divide(other)
operator fun FloatExpression.div(other: ObservableNumberValue): FloatBinding = divide(other) as FloatBinding
operator fun FloatExpression.div(other: ObservableDoubleValue): DoubleBinding = divide(other) as DoubleBinding

operator fun FloatProperty.divAssign(other: Number) { value /= other.toFloat() }
operator fun FloatProperty.divAssign(other: ObservableNumberValue) { value /= other.floatValue() }


operator fun FloatExpression.rem(other: Number): FloatBinding = floatBinding(this) { get() % other.toFloat() }
operator fun FloatExpression.rem(other: Double): DoubleBinding = doubleBinding(this) { get() % other }
operator fun FloatExpression.rem(other: ObservableNumberValue): FloatBinding = floatBinding(this, other) { get() % other.floatValue() }
operator fun FloatExpression.rem(other: ObservableDoubleValue): DoubleBinding = doubleBinding(this, other) { get() % other.get() }

operator fun FloatProperty.remAssign(other: Number) { value %= other.toFloat() }
operator fun FloatProperty.remAssign(other: ObservableNumberValue) { value %= other.floatValue() }

operator fun ObservableFloatValue.compareTo(other: Number): Int {
    if (get() > other.toFloat())
        return 1
    else if (get() < other.toFloat())
        return -1
    else
        return 0
}

operator fun ObservableFloatValue.compareTo(other: ObservableNumberValue): Int {
    if (get() > other.floatValue())
        return 1
    else if (get() < other.floatValue())
        return -1
    else
        return 0
}


operator fun IntegerExpression.plus(other: Int): IntegerBinding = add(other)
operator fun IntegerExpression.plus(other: Long): LongBinding = add(other)
operator fun IntegerExpression.plus(other: Float): FloatBinding = add(other)
operator fun IntegerExpression.plus(other: Double): DoubleBinding = add(other)
operator fun IntegerExpression.plus(other: ObservableIntegerValue): IntegerBinding = add(other) as IntegerBinding
operator fun IntegerExpression.plus(other: ObservableLongValue): LongBinding = add(other) as LongBinding
operator fun IntegerExpression.plus(other: ObservableFloatValue): FloatBinding = add(other) as FloatBinding
operator fun IntegerExpression.plus(other: ObservableDoubleValue): DoubleBinding = add(other) as DoubleBinding

operator fun IntegerProperty.plusAssign(other: Number) { value += other.toInt() }
operator fun IntegerProperty.plusAssign(other: ObservableNumberValue) { value += other.intValue() }

operator fun IntegerProperty.inc(): IntegerProperty {
    value++
    return this
}

operator fun IntegerExpression.minus(other: Int): IntegerBinding = subtract(other)
operator fun IntegerExpression.minus(other: Long): LongBinding = subtract(other)
operator fun IntegerExpression.minus(other: Float): FloatBinding = subtract(other)
operator fun IntegerExpression.minus(other: Double): DoubleBinding = subtract(other)
operator fun IntegerExpression.minus(other: ObservableIntegerValue): IntegerBinding = subtract(other) as IntegerBinding
operator fun IntegerExpression.minus(other: ObservableLongValue): LongBinding = subtract(other) as LongBinding
operator fun IntegerExpression.minus(other: ObservableFloatValue): FloatBinding = subtract(other) as FloatBinding
operator fun IntegerExpression.minus(other: ObservableDoubleValue): DoubleBinding = subtract(other) as DoubleBinding

operator fun IntegerProperty.minusAssign(other: Number) { value -= other.toInt() }
operator fun IntegerProperty.minusAssign(other: ObservableNumberValue) { value -= other.intValue() }

operator fun IntegerExpression.unaryMinus(): IntegerBinding = negate()

operator fun IntegerProperty.dec(): IntegerProperty {
    value--
    return this
}

operator fun IntegerExpression.times(other: Int): IntegerBinding = multiply(other)
operator fun IntegerExpression.times(other: Long): LongBinding = multiply(other)
operator fun IntegerExpression.times(other: Float): FloatBinding = multiply(other)
operator fun IntegerExpression.times(other: Double): DoubleBinding = multiply(other)
operator fun IntegerExpression.times(other: ObservableIntegerValue): IntegerBinding = multiply(other) as IntegerBinding
operator fun IntegerExpression.times(other: ObservableLongValue): LongBinding = multiply(other) as LongBinding
operator fun IntegerExpression.times(other: ObservableFloatValue): FloatBinding = multiply(other) as FloatBinding
operator fun IntegerExpression.times(other: ObservableDoubleValue): DoubleBinding = multiply(other) as DoubleBinding

operator fun IntegerProperty.timesAssign(other: Number) { value *= other.toInt() }
operator fun IntegerProperty.timesAssign(other: ObservableNumberValue) { value *= other.intValue() }

operator fun IntegerExpression.div(other: Int): IntegerBinding = divide(other)
operator fun IntegerExpression.div(other: Long): LongBinding = divide(other)
operator fun IntegerExpression.div(other: Float): FloatBinding = divide(other)
operator fun IntegerExpression.div(other: Double): DoubleBinding = divide(other)
operator fun IntegerExpression.div(other: ObservableIntegerValue): IntegerBinding = divide(other) as IntegerBinding
operator fun IntegerExpression.div(other: ObservableLongValue): LongBinding = divide(other) as LongBinding
operator fun IntegerExpression.div(other: ObservableFloatValue): FloatBinding = divide(other) as FloatBinding
operator fun IntegerExpression.div(other: ObservableDoubleValue): DoubleBinding = divide(other) as DoubleBinding

operator fun IntegerProperty.divAssign(other: Number) { value /= other.toInt() }
operator fun IntegerProperty.divAssign(other: ObservableNumberValue) { value /= other.intValue() }

operator fun IntegerExpression.rem(other: Int): IntegerBinding = integerBinding(this) { get() % other }
operator fun IntegerExpression.rem(other: Long): LongBinding = longBinding(this) { get() % other }
operator fun IntegerExpression.rem(other: Float): FloatBinding = floatBinding(this) { get() % other }
operator fun IntegerExpression.rem(other: Double): DoubleBinding = doubleBinding(this) { get() % other }
operator fun IntegerExpression.rem(other: ObservableIntegerValue): IntegerBinding = integerBinding(this, other) { get() % other.get() }
operator fun IntegerExpression.rem(other: ObservableLongValue): LongBinding = longBinding(this, other) { get() % other.get() }
operator fun IntegerExpression.rem(other: ObservableFloatValue): FloatBinding = floatBinding(this, other) { get() % other.get() }
operator fun IntegerExpression.rem(other: ObservableDoubleValue): DoubleBinding = doubleBinding(this, other) { get() % other.get() }

operator fun IntegerProperty.remAssign(other: Number) { value %= other.toInt() }
operator fun IntegerProperty.remAssign(other: ObservableNumberValue) { value %= other.intValue() }

operator fun ObservableIntegerValue.rangeTo(other: ObservableIntegerValue): Sequence<IntegerProperty> {
    val sequence = mutableListOf<IntegerProperty>()
    for (i in get()..other.get()) {
        sequence += SimpleIntegerProperty(i)
    }
    return sequence.asSequence()
}

operator fun ObservableIntegerValue.rangeTo(other: Int): Sequence<IntegerProperty> {
    val sequence = mutableListOf<IntegerProperty>()
    for (i in get()..other) {
        sequence += SimpleIntegerProperty(i)
    }
    return sequence.asSequence()
}

operator fun ObservableIntegerValue.rangeTo(other: ObservableLongValue): Sequence<LongProperty> {
    val sequence = mutableListOf<LongProperty>()
    for (i in get()..other.get()) {
        sequence += SimpleLongProperty(i)
    }
    return sequence.asSequence()
}

operator fun ObservableIntegerValue.rangeTo(other: Long): Sequence<LongProperty> {
    val sequence = mutableListOf<LongProperty>()
    for (i in get()..other) {
        sequence += SimpleLongProperty(i)
    }
    return sequence.asSequence()
}

operator fun ObservableIntegerValue.compareTo(other: Number): Int {
    if (get() > other.toDouble())
        return 1
    else if (get() < other.toDouble())
        return -1
    else
        return 0
}

operator fun ObservableIntegerValue.compareTo(other: ObservableNumberValue): Int {
    if (get() > other.doubleValue())
        return 1
    else if (get() < other.doubleValue())
        return -1
    else
        return 0
}


operator fun LongExpression.plus(other: Number): LongBinding = add(other.toLong())
operator fun LongExpression.plus(other: Float): FloatBinding = add(other)
operator fun LongExpression.plus(other: Double): DoubleBinding = add(other)
operator fun LongExpression.plus(other: ObservableNumberValue): LongBinding = add(other) as LongBinding
operator fun LongExpression.plus(other: ObservableFloatValue): FloatBinding = add(other) as FloatBinding
operator fun LongExpression.plus(other: ObservableDoubleValue): DoubleBinding = add(other) as DoubleBinding

operator fun LongProperty.plusAssign(other: Number) { value += other.toLong() }
operator fun LongProperty.plusAssign(other: ObservableNumberValue) { value += other.longValue() }

operator fun LongProperty.inc(): LongProperty {
    value++
    return this
}

operator fun LongExpression.minus(other: Number): LongBinding = subtract(other.toLong())
operator fun LongExpression.minus(other: Float): FloatBinding = subtract(other)
operator fun LongExpression.minus(other: Double): DoubleBinding = subtract(other)
operator fun LongExpression.minus(other: ObservableNumberValue): LongBinding = subtract(other) as LongBinding
operator fun LongExpression.minus(other: ObservableFloatValue): FloatBinding = subtract(other) as FloatBinding
operator fun LongExpression.minus(other: ObservableDoubleValue): DoubleBinding = subtract(other) as DoubleBinding

operator fun LongProperty.minusAssign(other: Number) { value -= other.toLong() }
operator fun LongProperty.minusAssign(other: ObservableNumberValue) { value -= other.longValue() }

operator fun LongExpression.unaryMinus(): LongBinding = negate()

operator fun LongProperty.dec(): LongProperty {
    value--
    return this
}

operator fun LongExpression.times(other: Number): LongBinding = multiply(other.toLong())
operator fun LongExpression.times(other: Float): FloatBinding = multiply(other)
operator fun LongExpression.times(other: Double): DoubleBinding = multiply(other)
operator fun LongExpression.times(other: ObservableNumberValue): LongBinding = multiply(other) as LongBinding
operator fun LongExpression.times(other: ObservableFloatValue): FloatBinding = multiply(other) as FloatBinding
operator fun LongExpression.times(other: ObservableDoubleValue): DoubleBinding = multiply(other) as DoubleBinding

operator fun LongProperty.timesAssign(other: Number) { value *= other.toLong() }
operator fun LongProperty.timesAssign(other: ObservableNumberValue) { value *= other.longValue() }

operator fun LongExpression.div(other: Number): LongBinding = divide(other.toLong())
operator fun LongExpression.div(other: Float): FloatBinding = divide(other)
operator fun LongExpression.div(other: Double): DoubleBinding = divide(other)
operator fun LongExpression.div(other: ObservableNumberValue): LongBinding = divide(other) as LongBinding
operator fun LongExpression.div(other: ObservableFloatValue): FloatBinding = divide(other) as FloatBinding
operator fun LongExpression.div(other: ObservableDoubleValue): DoubleBinding = divide(other) as DoubleBinding

operator fun LongProperty.divAssign(other: Number) { value /= other.toLong() }
operator fun LongProperty.divAssign(other: ObservableNumberValue) { value /= other.longValue() }

operator fun LongExpression.rem(other: Number): LongBinding = longBinding(this) { get() % other.toLong() }
operator fun LongExpression.rem(other: Float): FloatBinding = floatBinding(this) { get() % other }
operator fun LongExpression.rem(other: Double): DoubleBinding = doubleBinding(this) { get() % other }

operator fun LongExpression.rem(other: ObservableNumberValue): LongBinding = longBinding(this, other) { this.get() % other.longValue() }
operator fun LongExpression.rem(other: ObservableFloatValue): FloatBinding = floatBinding(this, other) { this.get() % other.get() }
operator fun LongExpression.rem(other: ObservableDoubleValue): DoubleBinding = doubleBinding(this, other) { this.get() % other.get() }

operator fun LongProperty.remAssign(other: Number) { value %= other.toLong() }
operator fun LongProperty.remAssign(other: ObservableNumberValue) { value %= other.longValue() }

operator fun ObservableLongValue.rangeTo(other: ObservableLongValue): Sequence<LongProperty> {
    val sequence = mutableListOf<LongProperty>()
    for (i in get()..other.get()) {
        sequence += SimpleLongProperty(i)
    }
    return sequence.asSequence()
}

operator fun ObservableLongValue.rangeTo(other: Long): Sequence<LongProperty> {
    val sequence = mutableListOf<LongProperty>()
    for (i in get()..other) {
        sequence += SimpleLongProperty(i)
    }
    return sequence.asSequence()
}

operator fun ObservableLongValue.rangeTo(other: ObservableIntegerValue): Sequence<LongProperty> {
    val sequence = mutableListOf<LongProperty>()
    for (i in get()..other.get()) {
        sequence += SimpleLongProperty(i)
    }
    return sequence.asSequence()
}

operator fun ObservableLongValue.rangeTo(other: Int): Sequence<LongProperty> {
    val sequence = mutableListOf<LongProperty>()
    for (i in get()..other) {
        sequence += SimpleLongProperty(i)
    }
    return sequence.asSequence()
}

operator fun ObservableLongValue.compareTo(other: Number): Int {
    if (get() > other.toDouble())
        return 1
    else if (get() < other.toDouble())
        return -1
    else
        return 0
}

operator fun ObservableLongValue.compareTo(other: ObservableNumberValue): Int {
    if (get() > other.doubleValue())
        return 1
    else if (get() < other.doubleValue())
        return -1
    else
        return 0
}


infix fun NumberExpression.gt(other: Int): BooleanBinding = greaterThan(other)
infix fun NumberExpression.gt(other: Long): BooleanBinding = greaterThan(other)
infix fun NumberExpression.gt(other: Float): BooleanBinding = greaterThan(other)
infix fun NumberExpression.gt(other: Double): BooleanBinding = greaterThan(other)
infix fun NumberExpression.gt(other: ObservableNumberValue): BooleanBinding = greaterThan(other)

infix fun NumberExpression.ge(other: Int): BooleanBinding = greaterThanOrEqualTo(other)
infix fun NumberExpression.ge(other: Long): BooleanBinding = greaterThanOrEqualTo(other)
infix fun NumberExpression.ge(other: Float): BooleanBinding = greaterThanOrEqualTo(other)
infix fun NumberExpression.ge(other: Double): BooleanBinding = greaterThanOrEqualTo(other)
infix fun NumberExpression.ge(other: ObservableNumberValue): BooleanBinding = greaterThanOrEqualTo(other)

infix fun NumberExpression.eq(other: Int): BooleanBinding = isEqualTo(other)
infix fun NumberExpression.eq(other: Long): BooleanBinding = isEqualTo(other)
infix fun NumberExpression.eq(other: ObservableNumberValue): BooleanBinding = isEqualTo(other)

infix fun NumberExpression.le(other: Int): BooleanBinding = lessThanOrEqualTo(other)
infix fun NumberExpression.le(other: Long): BooleanBinding = lessThanOrEqualTo(other)
infix fun NumberExpression.le(other: Float): BooleanBinding = lessThanOrEqualTo(other)
infix fun NumberExpression.le(other: Double): BooleanBinding = lessThanOrEqualTo(other)
infix fun NumberExpression.le(other: ObservableNumberValue): BooleanBinding = lessThanOrEqualTo(other)

infix fun NumberExpression.lt(other: Int): BooleanBinding = lessThan(other)
infix fun NumberExpression.lt(other: Long): BooleanBinding = lessThan(other)
infix fun NumberExpression.lt(other: Float): BooleanBinding = lessThan(other)
infix fun NumberExpression.lt(other: Double): BooleanBinding = lessThan(other)
infix fun NumberExpression.lt(other: ObservableNumberValue): BooleanBinding = lessThan(other)


@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
operator fun BooleanExpression.not(): BooleanBinding = not()

infix fun BooleanExpression.and(other: Boolean): BooleanBinding = and(SimpleBooleanProperty(other))
infix fun BooleanExpression.and(other: ObservableBooleanValue): BooleanBinding = and(other)

infix fun BooleanExpression.or(other: Boolean): BooleanBinding = or(SimpleBooleanProperty(other))
infix fun BooleanExpression.or(other: ObservableBooleanValue): BooleanBinding = or(other)

infix fun BooleanExpression.xor(other: Boolean): BooleanBinding = booleanBinding(this) { get() xor other }
infix fun BooleanExpression.xor(other: ObservableBooleanValue): BooleanBinding = booleanBinding(this, other) { get() xor other.get() }

infix fun BooleanExpression.eq(other: Boolean): BooleanBinding = isEqualTo(SimpleBooleanProperty(other))
infix fun BooleanExpression.eq(other: ObservableBooleanValue): BooleanBinding = isEqualTo(other)


operator fun StringExpression.plus(other: Any): StringExpression = concat(other)
operator fun StringProperty.plusAssign(other: Any) { value += other }

operator fun StringExpression.get(index: Int): Binding<Char?> = objectBinding(this) {
    if (index < get().length)
        get()[index]
    else
        null
}

operator fun StringExpression.get(index: ObservableIntegerValue): Binding<Char?> = objectBinding(this, index) {
    if (index < get().length)
        get()[index.get()]
    else
        null
}

operator fun StringExpression.get(start: Int, end: Int): StringBinding = stringBinding(this) { get().subSequence(start, end).toString() }
operator fun StringExpression.get(start: ObservableIntegerValue, end: Int): StringBinding = stringBinding(this, start) { get().subSequence(start.get(), end).toString() }
operator fun StringExpression.get(start: Int, end: ObservableIntegerValue): StringBinding = stringBinding(this, end) { get().subSequence(start, end.get()).toString() }
operator fun StringExpression.get(start: ObservableIntegerValue, end: ObservableIntegerValue): StringBinding = stringBinding(this, start, end) { get().subSequence(start.get(), end.get()).toString() }

operator fun StringExpression.unaryMinus(): StringBinding = stringBinding(this) { get().reversed() }

operator fun StringExpression.compareTo(other: String): Int = get().compareTo(other)
operator fun StringExpression.compareTo(other: ObservableStringValue): Int = get().compareTo(other.get())

infix fun StringExpression.gt(other: String): BooleanBinding = greaterThan(other)
infix fun StringExpression.gt(other: ObservableStringValue): BooleanBinding = greaterThan(other)

infix fun StringExpression.ge(other: String): BooleanBinding = greaterThanOrEqualTo(other)
infix fun StringExpression.ge(other: ObservableStringValue): BooleanBinding = greaterThanOrEqualTo(other)

infix fun StringExpression.eq(other: String): BooleanBinding = isEqualTo(other)
infix fun StringExpression.eq(other: ObservableStringValue): BooleanBinding = isEqualTo(other)

infix fun StringExpression.le(other: String): BooleanBinding = lessThanOrEqualTo(other)
infix fun StringExpression.le(other: ObservableStringValue): BooleanBinding = lessThanOrEqualTo(other)

infix fun StringExpression.lt(other: String): BooleanBinding = lessThan(other)
infix fun StringExpression.lt(other: ObservableStringValue): BooleanBinding = lessThan(other)

infix fun StringExpression.eqIgnoreCase(other: String): BooleanBinding = isEqualToIgnoreCase(other)
infix fun StringExpression.eqIgnoreCase(other: ObservableStringValue): BooleanBinding = isEqualToIgnoreCase(other)


fun <T> ObservableValue<T>.integerBinding(vararg dependencies: Observable, op: (T?) -> Int): IntegerBinding
        = Bindings.createIntegerBinding(Callable { op(value) }, this, *dependencies)

fun <T : Any> integerBinding(receiver: T, vararg dependencies: Observable, op: T.() -> Int): IntegerBinding
        = Bindings.createIntegerBinding(Callable { receiver.op() }, *createObservableArray(receiver, *dependencies))

fun <T> ObservableValue<T>.longBinding(vararg dependencies: Observable, op: (T?) -> Long): LongBinding
        = Bindings.createLongBinding(Callable { op(value) }, this, *dependencies)

fun <T : Any> longBinding(receiver: T, vararg dependencies: Observable, op: T.() -> Long): LongBinding
        = Bindings.createLongBinding(Callable { receiver.op() }, *createObservableArray(receiver, *dependencies))

fun <T> ObservableValue<T>.doubleBinding(vararg dependencies: Observable, op: (T?) -> Double): DoubleBinding
        = Bindings.createDoubleBinding(Callable { op(value) }, this, *dependencies)

fun <T : Any> doubleBinding(receiver: T, vararg dependencies: Observable, op: T.() -> Double): DoubleBinding
        = Bindings.createDoubleBinding(Callable { receiver.op() }, *createObservableArray(receiver, *dependencies))

fun <T> ObservableValue<T>.floatBinding(vararg dependencies: Observable, op: (T?) -> Float): FloatBinding
        = Bindings.createFloatBinding(Callable { op(value) }, this, *dependencies)

fun <T : Any> floatBinding(receiver: T, vararg dependencies: Observable, op: T.() -> Float): FloatBinding
        = Bindings.createFloatBinding(Callable { receiver.op() }, *createObservableArray(receiver, *dependencies))

fun <T> ObservableValue<T>.booleanBinding(vararg dependencies: Observable, op: (T?) -> Boolean): BooleanBinding =
        Bindings.createBooleanBinding(Callable { op(value) }, this, *dependencies)

fun <T : Any> booleanBinding(receiver: T, vararg dependencies: Observable, op: T.() -> Boolean): BooleanBinding
        = Bindings.createBooleanBinding(Callable { receiver.op() }, *createObservableArray(receiver, *dependencies))

/**
 * A Boolean binding that tracks all items in an observable list and create an observable boolean
 * value by anding together an observable boolean representing each element in the observable list.
 * Whenever the list changes, the binding is updated as well
 */
fun <T : Any> booleanListBinding(list: ObservableList<T>, itemToBooleanExpr: T.() -> BooleanExpression): BooleanExpression {
    val facade = SimpleBooleanProperty()
    fun rebind() {
        if (list.isEmpty()) {
            facade.unbind()
            facade.value = false
        } else {
            facade.cleanBind(list.map(itemToBooleanExpr).reduce { a, b -> a.and(b) })
        }
    }
    list.onChange { rebind() }
    rebind()
    return facade
}

fun <T> ObservableValue<T>.stringBinding(vararg dependencies: Observable, op: (T?) -> String?): StringBinding
        = Bindings.createStringBinding(Callable { op(value) }, this, *dependencies)

fun <T : Any> stringBinding(receiver: T, vararg dependencies: Observable, op: T.() -> String?): StringBinding =
        Bindings.createStringBinding(Callable { receiver.op() }, *createObservableArray(receiver, *dependencies))

fun <T, R> ObservableValue<T>.objectBinding(vararg dependencies: Observable, op: (T?) -> R?): Binding<R?>
        = Bindings.createObjectBinding(Callable { op(value) }, this, *dependencies)

fun <T : Any, R> objectBinding(receiver: T, vararg dependencies: Observable, op: T.() -> R?): ObjectBinding<R?>
        = Bindings.createObjectBinding(Callable { receiver.op() }, *createObservableArray(receiver, *dependencies))

fun <T : Any, R> nonNullObjectBinding(receiver: T, vararg dependencies: Observable, op: T.() -> R): ObjectBinding<R>
        = Bindings.createObjectBinding(Callable { receiver.op() }, *createObservableArray(receiver, *dependencies))

private fun <T> createObservableArray(receiver: T, vararg dependencies: Observable): Array<out Observable> =
        if (receiver is Observable) arrayOf(receiver, *dependencies) else dependencies

/**
 * Assign the value from the creator to this WritableValue if and only if it is currently null
 */
fun <T> WritableValue<T>.assignIfNull(creator: () -> T) {
    if (value == null) value = creator()
}

fun Double.toProperty(): DoubleProperty = SimpleDoubleProperty(this)
fun Float.toProperty(): FloatProperty = SimpleFloatProperty(this)
fun Long.toProperty(): LongProperty = SimpleLongProperty(this)
fun Int.toProperty(): IntegerProperty = SimpleIntegerProperty(this)
fun Boolean.toProperty(): BooleanProperty = SimpleBooleanProperty(this)
fun String.toProperty(): StringProperty = SimpleStringProperty(this)

fun String?.toProperty() = SimpleStringProperty(this ?: "")
fun Double?.toProperty() = SimpleDoubleProperty(this ?: 0.0)
fun Float?.toProperty() = SimpleFloatProperty(this ?: 0.0F)
fun Long?.toProperty() = SimpleLongProperty(this ?: 0L)
fun Boolean?.toProperty() = SimpleBooleanProperty(this ?: false)
fun <T : Any> T?.toProperty() = SimpleObjectProperty<T>(this)

class WeakReferenceDelegate<T>(val creator: () -> T) {
    var weakReference : WeakReference<T>? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        var ret = weakReference?.get()
        if (ret == null) {
            ret = creator()
            weakReference = WeakReference(ret)
        }
        return ret!!
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {

    }
}