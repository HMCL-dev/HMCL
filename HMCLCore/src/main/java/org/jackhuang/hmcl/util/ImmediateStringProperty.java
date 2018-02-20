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

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.util.Objects;
import java.util.function.Consumer;

/**
 *
 * @author huangyuhui
 */
public class ImmediateStringProperty extends SimpleStringProperty {

    @Override
    public void set(String newValue) {
        super.get();
        super.set(newValue);
    }

    @Override
    public void bind(ObservableValue<? extends String> newObservable) {
        super.get();
        super.bind(newObservable);
    }

    @Override
    public void unbind() {
        super.get();
        super.unbind();
    }

    private Consumer<String> consumer = null;
    private ChangeListener<String> listener = null;

    public void setChangedListener(Consumer<String> consumer) {
        this.consumer = Objects.requireNonNull(consumer);
        this.listener = null;
    }

    public void setChangedListener(ChangeListener<String> listener) {
        this.consumer = null;
        this.listener = Objects.requireNonNull(listener);
    }

    public ImmediateStringProperty(Object bean, String name, String initialValue) {
        super(bean, name, initialValue);
        ChangeListener<String> changeListener = (a, b, newValue) -> {
            if (consumer != null)
                consumer.accept(newValue);
            if (listener != null)
                listener.changed(a, b, newValue);
        };
        addListener(changeListener);
    }
}
