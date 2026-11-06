// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/// Operating system categories and their platform identifiers, independent of the current machine.
///
/// @author huangyuhui
@NotNullByDefault
public enum OperatingSystem {
    /// Microsoft Windows.
    WINDOWS("windows"),

    /// Linux and the fallback category for generic Unix names, including Solaris and SunOS.
    LINUX("linux"),

    /// macOS.
    MACOS("macos", "osx"),

    /// FreeBSD.
    FREEBSD("freebsd", "linux"),

    /// Unrecognized or unspecified operating system, identified as `universal`.
    UNKNOWN("universal");

    /// Identifier used in HMCL platform names.
    private final String checkedName;

    /// Identifier used to select Minecraft libraries and evaluate operating system restrictions.
    private final String mojangName;

    /// Creates a category with the same HMCL and Minecraft identifier.
    OperatingSystem(String checkedName) {
        this.checkedName = checkedName;
        this.mojangName = checkedName;
    }

    /// Creates a category with separate HMCL and Minecraft identifiers.
    OperatingSystem(String checkedName, String mojangName) {
        this.checkedName = checkedName;
        this.mojangName = mojangName;
    }

    /// Returns the HMCL identifier, such as `macos` or `freebsd`.
    public String getCheckedName() {
        return checkedName;
    }

    /// Returns the Minecraft identifier, mapping macOS to `osx` and FreeBSD to `linux`.
    public String getMojangName() {
        return mojangName;
    }

    /// Returns whether this category is Linux, FreeBSD, or macOS; unknown systems return `false`.
    public boolean isUnixLike() {
        return this == LINUX || this == FREEBSD || this == MACOS;
    }

    /// Returns `java.exe` for Windows and `java` for all other categories, including unknown systems.
    public String getJavaExecutable() {
        return this == WINDOWS ? "java.exe" : "java";
    }

    /// Parses a system name case-insensitively, ignoring surrounding whitespace.
    /// Recognizes common JVM names and aliases without consulting the current machine.
    /// Returns [#UNKNOWN] for `null`, blank, or unrecognized input.
    public static OperatingSystem parseOSName(@Nullable String name) {
        if (name == null) {
            return UNKNOWN;
        }

        name = name.trim().toLowerCase(Locale.ROOT);

        if (name.contains("mac") || name.contains("darwin") || name.contains("osx"))
            return MACOS;
        else if (name.contains("win"))
            return WINDOWS;
        else if (name.contains("solaris") || name.contains("linux") || name.contains("unix") || name.contains("sunos"))
            return LINUX;
        else if (name.equals("freebsd"))
            return FREEBSD;
        else
            return UNKNOWN;
    }
}
