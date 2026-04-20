/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.gradle.pack;

/// Debian packaging metadata for one HMCL release type.
///
/// The package name, installed command, desktop file, and alternatives
/// priority are intentionally centralized here so `CreateDeb` can stay focused
/// on archive layout instead of duplicating channel-specific branching.
public enum ReleaseType {
    STABLE("stable", "hmcl", 300),
    DEVELOPMENT("dev", "hmcl-dev", 200),
    NIGHTLY("nightly", "hmcl-nightly", 100);

    public final String channelName;
    private final String packageName;
    private final String launcherCommandName;
    private final String desktopFileName;
    private final int alternativesPriority;

    ReleaseType(String channelName, String packageName, int alternativesPriority) {
        this.channelName = channelName;
        this.packageName = packageName;
        this.launcherCommandName = "hmcl-" + channelName;
        this.desktopFileName = "hmcl-" + channelName + ".desktop";
        this.alternativesPriority = alternativesPriority;
    }

    /// Debian package name written into `control` and used in the output filename.
    public String getPackageName() {
        return packageName;
    }

    /// Actual executable installed under `/usr/bin/`.
    public String getLauncherCommandName() {
        return launcherCommandName;
    }

    /// Desktop entry filename installed under `/usr/share/applications/`.
    public String getDesktopFileName() {
        return desktopFileName;
    }

    /// Priority used when registering the generic `hmcl` alias.
    ///
    /// Higher values win, which keeps `stable` as the default alias target when
    /// multiple channel packages are installed together.
    public int getAlternativesPriority() {
        return alternativesPriority;
    }
}
