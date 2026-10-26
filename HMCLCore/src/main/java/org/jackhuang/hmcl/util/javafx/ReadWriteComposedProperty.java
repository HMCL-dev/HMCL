// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.javafx;

import java.util.function.Consumer;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;

/**
 * @author yushijinhun
 */
public final class ReadWriteComposedProperty<T> extends SimpleObjectProperty<T> {

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final ObservableValue<T> readSource;
    private final Consumer<T> writeTarget;

    @SuppressWarnings("FieldCanBeLocal")
    private ChangeListener<T> listener;

    public ReadWriteComposedProperty(ObservableValue<T> readSource, Consumer<T> writeTarget) {
        this(null, "", readSource, writeTarget);
    }

    public ReadWriteComposedProperty(Object bean, String name, ObservableValue<T> readSource, Consumer<T> writeTarget) {
        super(bean, name);
        this.readSource = readSource;
        this.writeTarget = writeTarget;

        this.listener = (observable, oldValue, newValue) -> {
            if (!isBound()) {
                set(newValue);
            }
        };
        readSource.addListener(new WeakChangeListener<>(listener));
        set(readSource.getValue());
    }

    @Override
    protected void invalidated() {
        writeTarget.accept(get());
    }
}
