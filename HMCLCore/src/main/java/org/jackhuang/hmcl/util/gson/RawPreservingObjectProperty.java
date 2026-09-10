/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
