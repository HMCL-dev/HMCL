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
import javafx.beans.property.Property;
import org.jetbrains.annotations.Nullable;

/// If a Property implementing this interface fails to deserialize a json object into a value, it will store the original JsonElement internally.
/// If the value does not change at runtime, the original JsonElement will be written back during serialization.
///
/// @author Glavo
public interface RawPreservingProperty<T> extends Property<T> {
    void setRawJson(JsonElement value);

    @Nullable JsonElement getRawJson();
}
