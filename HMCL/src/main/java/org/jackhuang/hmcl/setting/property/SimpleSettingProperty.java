// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting.property;

import com.google.gson.JsonElement;
import javafx.beans.property.SimpleObjectProperty;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

/// @author Glavo
@NotNullByDefault
public class SimpleSettingProperty<T extends @UnknownNullability Object> extends SimpleObjectProperty<T>
        implements SettingProperty<T> {
    private @Nullable JsonElement rawJson;

    private final T defaultValue;

    public SimpleSettingProperty(GameSettings bean,
                                 String name,
                                 T defaultValue) {
        super(bean, name, defaultValue);
        this.defaultValue = defaultValue;
    }

    @Override
    public GameSettings getBean() {
        return (GameSettings) super.getBean();
    }

    @Override
    public T defaultValue() {
        return defaultValue;
    }

    @Override
    public @Nullable JsonElement getRawJson() {
        return rawJson;
    }

    @Override
    public void setRawJson(@Nullable JsonElement rawJson) {
        this.rawJson = rawJson;
    }

                  /// Clears preserved raw JSON after the property value has been changed.
    @Override
    protected void invalidated() {
        this.rawJson = null;
    }
}
