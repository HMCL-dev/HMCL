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
package org.jackhuang.hmcl.util;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.util.Objects;
import java.util.function.Consumer;

/**
 *
 * @author huangyuhui
 */
public class ImmediateObjectProperty<T> extends SimpleObjectProperty<T> {

    @Override
    public void set(T newValue) {
        super.get();
        super.set(newValue);
    }

    @Override
    public void bind(ObservableValue<? extends T> newObservable) {
        super.get();
        super.bind(newObservable);
    }

    @Override
    public void unbind() {
        super.get();
        super.unbind();
    }

    private Consumer<T> listener = Lang.emptyConsumer();
    private final ChangeListener<T> changeListener = (a, b, newValue) -> listener.accept(newValue);

    public void setChangedListener(Consumer<T> listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    public void setChangedListenerAndOperate(Consumer<T> listener) {
        this.listener = Objects.requireNonNull(listener);
        listener.accept(get());
    }

    public ImmediateObjectProperty(Object bean, String name, T initialValue) {
        super(bean, name, initialValue);
        addListener(changeListener);
    }
}