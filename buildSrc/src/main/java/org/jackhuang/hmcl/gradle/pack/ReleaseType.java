// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gradle.pack;

/// Debian packaging metadata for one HMCL release type.
///
/// The package name, installed command, desktop file, and alternatives
/// priority are intentionally centralized here so `CreateDeb` can stay focused
/// on archive layout instead of duplicating channel-specific branching.
public enum ReleaseType {
    STABLE("stable", "hmcl", "HMCL", 100),
    DEVELOPMENT("beta", "hmcl-beta", "HMCL (Beta)", 200),
    NIGHTLY("nightly", "hmcl-nightly", "HMCL (Nightly)", 300);

    private final String name;
    private final String packageName;
    private final String displayName;
    private final int alternativesPriority;

    ReleaseType(String name, String packageName, String displayName, int alternativesPriority) {
        this.name = name;
        this.packageName = packageName;
        this.displayName = displayName;
        this.alternativesPriority = alternativesPriority;
    }

    ///
    public String getName() {
        return name;
    }

    /// Debian package name written into `control` and used in the output filename.
    public String getPackageName() {
        return packageName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /// Priority used when registering the generic `hmcl` alias.
    public int getAlternativesPriority() {
        return alternativesPriority;
    }
}
