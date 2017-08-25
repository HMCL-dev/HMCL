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

import javafx.beans.value.*
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList

fun <T> ObservableValue<T>.onChange(op: (T?) -> Unit) = apply { addListener { _, _, new -> op(new) } }
fun <T> ObservableValue<T>.onChangeAndOperate(op: (T?) -> Unit) = apply { addListener { _, _, new -> op(new) }; op(value) }
fun <T> ObservableValue<T>.onChangeAndOperateWeakly(op: (T?) -> Unit) = apply { addListener(WeakChangeListener { _, _, new -> op(new) }); op(value) }
fun ObservableBooleanValue.onChange(op: (Boolean) -> Unit) = apply { addListener { _, _, new -> op(new ?: false) } }
fun ObservableIntegerValue.onChange(op: (Int) -> Unit) = apply { addListener { _, _, new -> op((new ?: 0).toInt()) } }
fun ObservableLongValue.onChange(op: (Long) -> Unit) = apply { addListener { _, _, new -> op((new ?: 0L).toLong()) } }
fun ObservableFloatValue.onChange(op: (Float) -> Unit) = apply { addListener { _, _, new -> op((new ?: 0f).toFloat()) } }
fun ObservableDoubleValue.onChange(op: (Double) -> Unit) = apply { addListener { _, _, new -> op((new ?: 0.0).toDouble()) } }
fun <T> ObservableList<T>.onChange(op: (ListChangeListener.Change<out T>) -> Unit) = apply {
    addListener(ListChangeListener { op(it) })
}
