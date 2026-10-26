// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

/// @author Glavo
public enum DefaultIsolationType {
    /// Default to isolate all instances.
    ALWAYS,

    /// Default to isolate instances with any modloader.
    MODDED,

    /// No instances are isolated by default.
    NEVER,
}
