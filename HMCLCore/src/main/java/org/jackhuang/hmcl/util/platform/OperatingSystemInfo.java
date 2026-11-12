// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jetbrains.annotations.NotNullByDefault;

/// Immutable operating system display information.
///
/// @param name the system or distribution name, including a display release or release name when available
/// @param description the detailed system name and version, including distribution release information when available
@NotNullByDefault
public record OperatingSystemInfo(String name, String description) {
}
