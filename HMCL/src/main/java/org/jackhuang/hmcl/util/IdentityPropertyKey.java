// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A named property key whose equality is determined by the key string.
///
/// @param <T> the intended property value type, not checked at runtime
/// @param key the name used for equality and hashing
@NotNullByDefault
public record IdentityPropertyKey<T extends @Nullable Object>(String key) {
    /// Creates a key named after the class's binary name and the supplied property name.
    ///
    /// @param clazz the class qualifying the property name
    /// @param name the property name appended after a dot
    public IdentityPropertyKey(Class<?> clazz, String name) {
        this(clazz.getName() + "." + name);
    }
}
