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
package org.jackhuang.hmcl.util.javafx;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;

import java.util.function.Function;

/**
 * @author yushijinhun
 */
public class MappedProperty<T, U> extends SimpleObjectProperty<U> {

    private final Property<T> predecessor;
    private final Function<U, T> reservedMapper;

    private final ObjectBinding<U> binding;

    public MappedProperty(Property<T> predecessor, Function<T, U> mapper, Function<U, T> reservedMapper) {
        this(null, "", predecessor, mapper, reservedMapper);
    }

    public MappedProperty(Object bean, String name, Property<T> predecessor, Function<T, U> mapper, Function<U, T> reservedMapper) {
        super(bean, name);
        this.predecessor = predecessor;
        this.reservedMapper = reservedMapper;

        binding = new ObjectBinding<U>() {
            {
                bind(predecessor);
            }

            @Override
            protected U computeValue() {
                return mapper.apply(predecessor.getValue());
            }

            @Override
            protected void onInvalidating() {
                MappedProperty.this.fireValueChangedEvent();
            }
        };
    }

    @Override
    public U get() {
        return binding.get();
    }

    @Override
    public void set(U value) {
        predecessor.setValue(reservedMapper.apply(value));
    }

    @Override
    public void bind(ObservableValue<? extends U> observable) {
        predecessor.bind(Bindings.createObjectBinding(() -> reservedMapper.apply(observable.getValue()), observable));
    }

    @Override
    public void unbind() {
        predecessor.unbind();
    }

    @Override
    public boolean isBound() {
        return predecessor.isBound();
    }
}
