// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.macos;

import org.jackhuang.hmcl.util.platform.hardware.DeviceInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Immutable Mac identity preserving the detected name and model identifier without model lookup.
///
/// @param name the detected name, or the model identifier when no name is available
/// @param modelIdentifier the model identifier reported by macOS, or `null` if unavailable
@NotNullByDefault
public record MacDeviceInfo(String name, @Nullable String modelIdentifier) implements DeviceInfo {
}
