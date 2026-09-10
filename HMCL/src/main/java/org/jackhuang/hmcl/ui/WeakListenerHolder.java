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
package org.jackhuang.hmcl.ui;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.event.EventPriority;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class WeakListenerHolder {
    private final List<Object> refs = new ArrayList<>(0);

    public WeakListenerHolder() {
    }

    public WeakInvalidationListener weak(InvalidationListener listener) {
        refs.add(listener);
        return new WeakInvalidationListener(listener);
    }

    public <T> WeakChangeListener<T> weak(ChangeListener<T> listener) {
        refs.add(listener);
        return new WeakChangeListener<>(listener);
    }

    public <T> WeakListChangeListener<T> weak(ListChangeListener<T> listener) {
        refs.add(listener);
        return new WeakListChangeListener<>(listener);
    }

    public <T extends Event> void registerWeak(EventManager<T> manager, Consumer<T> consumer) {
        refs.add(manager.registerWeak(consumer));
    }

    public <T extends Event> void registerWeak(EventManager<T> manager, Consumer<T> consumer, EventPriority priority) {
        refs.add(manager.registerWeak(consumer, priority));
    }

    public <T> void onWeakChange(ObservableValue<T> value, Consumer<T> consumer) {
        refs.add(FXUtils.onWeakChange(value, consumer));
    }

    public <T> void onWeakChangeAndOperate(ObservableValue<T> value, Consumer<T> consumer) {
        refs.add(FXUtils.onWeakChangeAndOperate(value, consumer));
    }

    public void add(Object obj) {
        refs.add(obj);
    }

    public boolean remove(Object obj) {
        return refs.remove(obj);
    }

    public void clear() {
        refs.clear();
    }
}
