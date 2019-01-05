/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * @author huangyuhui
 * @deprecated Use SimpleObjectProperty instead
 */
@Deprecated
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

    public ImmediateObjectProperty(Object bean, String name, T initialValue) {
        super(bean, name, initialValue);
        ChangeListener<T> changeListener = (a, b, newValue) -> {
        };
        addListener(changeListener);
    }
}