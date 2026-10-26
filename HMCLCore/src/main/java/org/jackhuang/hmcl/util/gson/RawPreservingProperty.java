// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
