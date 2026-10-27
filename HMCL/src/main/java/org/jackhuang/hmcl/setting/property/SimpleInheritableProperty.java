// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting.property;

import org.jackhuang.hmcl.setting.GameSettings;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnknownNullability;

/// Stores a direct setting value that can be inherited by removing its name from `overrideProperties`.
///
/// @author Glavo
@NotNullByDefault
public final class SimpleInheritableProperty<T extends @UnknownNullability Object>
        extends SimpleSettingProperty<T>
        implements InheritableProperty<T> {

    /// Creates a property with the given owner, serialized name, and direct default value.
    public SimpleInheritableProperty(GameSettings bean, String name, T defaultValue) {
        super(bean, name, defaultValue);
    }
}
