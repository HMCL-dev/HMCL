// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.macos;

import org.jackhuang.hmcl.util.platform.hardware.DeviceInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Immutable Mac identity retaining its model identifier separately from the default display name.
///
/// @param name the fallback name; replaced with the mapped English model name when the identifier is known
/// @param modelIdentifier the model identifier reported by macOS, or `null` if unavailable
@NotNullByDefault
public record MacDeviceInfo(String name, @Nullable String modelIdentifier) implements DeviceInfo {

    /// Uses the mapped English model name when available, retaining the caller's fallback otherwise.
    public MacDeviceInfo {
        @Nullable MacModel model = MacModel.find(modelIdentifier);
        if (model != null)
            name = model.name();
    }

    /// Returns structured model details for localization, or `null` if the identifier is unknown.
    public @Nullable MacModel model() {
        return MacModel.find(modelIdentifier);
    }
}
