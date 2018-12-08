/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * @author huangyuhui
 * @deprecated Use SimpleIntegerProperty instead
 */
@Deprecated
public class ImmediateIntegerProperty extends SimpleIntegerProperty {

    @Override
    public void set(int newValue) {
        super.get();
        super.set(newValue);
    }

    @Override
    public void bind(ObservableValue<? extends Number> newObservable) {
        super.get();
        super.bind(newObservable);
    }

    @Override
    public void unbind() {
        super.get();
        super.unbind();
    }

    public ImmediateIntegerProperty(Object bean, String name, int initialValue) {
        super(bean, name, initialValue);
        ChangeListener<Number> changeListener = (a, b, newValue) -> {
        };
        addListener(changeListener);
    }
}
