// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting.property;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnknownNullability;

/// A setting value whose instance override state is stored in `GameSettings.Instance.overrideProperties`.
///
/// @author Glavo
@NotNullByDefault
public interface InheritableProperty<T extends @UnknownNullability Object> extends SettingProperty<T> {
}
