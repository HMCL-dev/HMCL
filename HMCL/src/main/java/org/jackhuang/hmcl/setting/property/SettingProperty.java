// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting.property;

import javafx.beans.property.Property;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.util.gson.RawPreservingProperty;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnknownNullability;

/// @author Glavo
@NotNullByDefault
public interface SettingProperty<T extends @UnknownNullability Object> extends Property<T>, RawPreservingProperty<T> {
    @Override
    GameSettings getBean();

    T defaultValue();
}
