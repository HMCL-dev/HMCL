// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
