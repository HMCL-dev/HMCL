// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.gson;

import com.google.gson.JsonElement;
import javafx.beans.property.SimpleObjectProperty;
import org.jetbrains.annotations.Nullable;

/// @author Glavo
/// @see ObservableSetting
public class RawPreservingObjectProperty<T> extends SimpleObjectProperty<T> implements RawPreservingProperty<T> {
    private JsonElement rawJson;

    public RawPreservingObjectProperty() {
    }

    public RawPreservingObjectProperty(T initialValue) {
        super(initialValue);
    }

    public RawPreservingObjectProperty(Object bean, String name) {
        super(bean, name);
    }

    public RawPreservingObjectProperty(Object bean, String name, T initialValue) {
        super(bean, name, initialValue);
    }

    @Override
    public void setRawJson(JsonElement value) {
        this.rawJson = value;
    }

    @Override
    public @Nullable JsonElement getRawJson() {
        return rawJson;
    }

    @Override
    protected void invalidated() {
        this.rawJson = null;
    }
}
