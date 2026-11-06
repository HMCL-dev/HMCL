// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/// Represents the operating system.
///
/// @author huangyuhui
public enum OperatingSystem {
    /// Microsoft Windows.
    WINDOWS("windows"),

    /// Linux and Unix like OS, including Solaris.
    LINUX("linux"),

    /// macOS.
    MACOS("macos", "osx"),

    /// FreeBSD.
    FREEBSD("freebsd", "linux"),

    /// Unknown operating system.
    UNKNOWN("universal");

    private final String checkedName;
    private final String mojangName;

    OperatingSystem(String checkedName) {
        this.checkedName = checkedName;
        this.mojangName = checkedName;
    }

    OperatingSystem(String checkedName, String mojangName) {
        this.checkedName = checkedName;
        this.mojangName = mojangName;
    }

    public String getCheckedName() {
        return checkedName;
    }

    public String getMojangName() {
        return mojangName;
    }

    public boolean isLinuxOrBSD() {
        return this == LINUX || this == FREEBSD;
    }

    public boolean isUnixLike() {
        return isLinuxOrBSD() || this == MACOS;
    }

    public String getJavaExecutable() {
        return this == WINDOWS ? "java.exe" : "java";
    }

    /// Parses a system name, returning unknown for null or unrecognized input.
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
