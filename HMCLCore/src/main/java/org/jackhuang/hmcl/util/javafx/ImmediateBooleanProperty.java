/*
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

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * @author huangyuhui
 * @deprecated Use SimpleBooleanProperty instead
 */
@Deprecated
public class ImmediateBooleanProperty extends SimpleBooleanProperty {

    @Override
    public void set(boolean newValue) {
        super.get();
        super.set(newValue);
    }

    @Override
    public void bind(ObservableValue<? extends Boolean> newObservable) {
        super.get();
        super.bind(newObservable);
    }

    @Override
    public void unbind() {
        super.get();
        super.unbind();
    }

    public ImmediateBooleanProperty(Object bean, String name, boolean initialValue) {
        super(bean, name, initialValue);
        ChangeListener<Boolean> changeListener = (a, b, newValue) -> {
        };
        addListener(changeListener);
    }
}