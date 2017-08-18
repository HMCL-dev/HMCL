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
package org.jackhuang.hmcl.util.property

import javafx.beans.property.*
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue

open class ImmediateStringProperty(bean: Any, name: String, initialValue: String): SimpleStringProperty(bean, name, initialValue) {

    override fun set(newValue: String) {
        super.get()
        super.set(newValue)
    }

    override fun bind(newObservable: ObservableValue<out String>) {
        super.get()
        super.bind(newObservable)
    }

    override fun unbind() {
        super.get()
        super.unbind()
    }

    private var myListener: (String) -> Unit = {}
    private val changeListener = ChangeListener<String> { _, _, newValue ->
        myListener(newValue)
    }

    fun setChangedListener(listener: (String) -> Unit) {
        myListener = listener
    }

    init {
        addListener(changeListener)
    }
}

open class ImmediateBooleanProperty(bean: Any, name: String, initialValue: Boolean): SimpleBooleanProperty(bean, name, initialValue) {

    override fun set(newValue: Boolean) {
        super.get()
        super.set(newValue)
    }

    override fun bind(rawObservable: ObservableValue<out Boolean>?) {
        super.get()
        super.bind(rawObservable)
    }

    override fun unbind() {
        super.get()
        super.unbind()
    }

    private var myListener: (Boolean) -> Unit = {}
    private val changeListener = ChangeListener<Boolean> { _, _, newValue ->
        myListener(newValue)
    }

    fun setChangedListener(listener: (Boolean) -> Unit) {
        myListener = listener
    }

    init {
        addListener(changeListener)
    }
}

open class ImmediateIntegerProperty(bean: Any, name: String, initialValue: Int): SimpleIntegerProperty(bean, name, initialValue) {

    override fun set(newValue: Int) {
        super.get()
        super.set(newValue)
    }

    override fun bind(rawObservable: ObservableValue<out Number>) {
        super.get()
        super.bind(rawObservable)
    }

    override fun unbind() {
        super.get()
        super.unbind()
    }

    private var myListener: (Int) -> Unit = {}
    private val changeListener = ChangeListener<Number> { _, _, newValue ->
        myListener(newValue.toInt())
    }

    fun setChangedListener(listener: (Int) -> Unit) {
        myListener = listener
    }

    init {
        addListener(changeListener)
    }
}

open class ImmediateDoubleProperty(bean: Any, name: String, initialValue: Double): SimpleDoubleProperty(bean, name, initialValue) {

    override fun set(newValue: Double) {
        super.get()
        super.set(newValue)
    }

    override fun bind(rawObservable: ObservableValue<out Number>) {
        super.get()
        super.bind(rawObservable)
    }

    override fun unbind() {
        super.get()
        super.unbind()
    }

    private var myListener: (Double) -> Unit = {}
    private val changeListener = ChangeListener<Number> { _, _, newValue ->
        myListener(newValue.toDouble())
    }

    fun setChangedListener(listener: (Double) -> Unit) {
        myListener = listener
    }

    init {
        addListener(changeListener)
    }
}

open class ImmediateObjectProperty<T>(bean: Any, name: String, initialValue: T): SimpleObjectProperty<T>(bean, name, initialValue) {

    override fun set(newValue: T) {
        super.get()
        super.set(newValue)
    }

    override fun bind(rawObservable: ObservableValue<out T>) {
        super.get()
        super.bind(rawObservable)
    }

    override fun unbind() {
        super.get()
        super.unbind()
    }

    private var myListener: (T) -> Unit = {}
    private val changeListener = ChangeListener<T> { _, _, newValue ->
        myListener(newValue)
    }

    fun setChangedListener(listener: (T) -> Unit) {
        myListener = listener
        listener(get())
    }

    init {
        addListener(changeListener)
    }
}