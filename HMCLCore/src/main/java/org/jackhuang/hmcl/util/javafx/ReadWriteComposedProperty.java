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

import java.util.function.Consumer;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;

/**
 * @author yushijinhun
 */
public class ReadWriteComposedProperty<T> extends SimpleObjectProperty<T> {

    @SuppressWarnings("unused")
    private final ObservableValue<T> readSource;
    private final Consumer<T> writeTarget;

    private ChangeListener<T> listener;

    public ReadWriteComposedProperty(ObservableValue<T> readSource, Consumer<T> writeTarget) {
        this(null, "", readSource, writeTarget);
    }

    public ReadWriteComposedProperty(Object bean, String name, ObservableValue<T> readSource, Consumer<T> writeTarget) {
        super(bean, name);
        this.readSource = readSource;
        this.writeTarget = writeTarget;

        this.listener = (observable, oldValue, newValue) -> set(newValue);
        readSource.addListener(new WeakChangeListener<>(listener));
        set(readSource.getValue());
    }

    @Override
    protected void invalidated() {
        writeTarget.accept(get());
    }
}
