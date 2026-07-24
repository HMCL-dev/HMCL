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

import org.jetbrains.annotations.NotNullByDefault;

/// Shared Linux package paths and generated file contents for HMCL packages.
///
/// Debian and RPM packages intentionally install the same launcher layout so
/// channel-specific commands, desktop entries, and alternatives registration
/// behave consistently across package formats.
@NotNullByDefault
final class LinuxPackageFiles {
    /// Generic command managed by the alternatives system.
    static final String COMMON_LAUNCHER_PATH = "/usr/bin/hmcl";

    /// Release channel metadata used for names and alternatives priority.
    private final ReleaseType releaseType;

    /// Bundled executable shell artifact produced by `makeExecutables`.
    private final String appFileName;

    /// Package manager name exposed to the launcher process.
    private final String packageManager;

    /// Creates package path helpers for one release channel and package manager.
    LinuxPackageFiles(ReleaseType releaseType, String appFileName, String packageManager) {
        this.releaseType = releaseType;
        this.appFileName = appFileName;
        this.packageManager = packageManager;
    }

    /// Returns the channel-specific command installed into `/usr/bin`.
    String launcherPath() {
        return "/usr/bin/hmcl-" + releaseType.getName();
    }

    /// Returns the installed location of the bundled HMCL shell artifact.
    String targetPath() {
        return "/usr/share/java/hmcl/" + appFileName;
    }

    /// Returns the installed desktop entry path for this channel.
    String desktopFilePath() {
        return "/usr/share/applications/hmcl-%s.desktop".formatted(releaseType.getName());
    }

    /// Returns the installed hicolor icon path for this channel.
    String iconTargetPath() {
        return "/usr/share/icons/hicolor/256x256/apps/hmcl-%s.png".formatted(releaseType.getName());
    }

    /// Creates a wrapper that launches the bundled shell artifact from the user's home directory.
    String launcherScript() {
        return """
                #!/usr/bin/env bash
                cd "$HOME"
                if [ -z "${HMCL_PACKAGE_MANAGED:-}" ]; then
                    export HMCL_PACKAGE_MANAGED="%s"
                fi
                if [ -z "${HMCL_USER_HOME:-}" ]; then
                    if [ -z "${XDG_DATA_HOME:-}" ]; then
                        export HMCL_USER_HOME="$HOME/.local/share/hmcl"
                    else
                        export HMCL_USER_HOME="$XDG_DATA_HOME/hmcl"
                    fi
                fi
                if [ -z "${HMCL_LOCAL_HOME:-}" ]; then
                    export HMCL_LOCAL_HOME="$HMCL_USER_HOME/local-%s"
                fi
                if [ -z "${HMCL_DEPENDENCIES_DIR:-}" ]; then
                    export HMCL_DEPENDENCIES_DIR="$HMCL_USER_HOME/dependencies"
                fi
                exec %s "$@"
                """.formatted(packageManager, releaseType.getName(), targetPath());
    }

    /// Creates the desktop entry that points to the channel-specific command.
    String desktopInfo() {
        return """
                [Desktop Entry]
                Type=Application
                Name=%s
                Comment=Hello Minecraft! Launcher
                Exec=%s
                Icon=%s
                Terminal=false
                StartupNotify=false
                Categories=Game;
                Keywords=mc;minecraft;
                """.formatted(releaseType.getDisplayName(), launcherPath(), iconTargetPath());
    }
}
