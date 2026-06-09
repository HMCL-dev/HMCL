/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.setting;

import org.jetbrains.annotations.NotNullByDefault;

/// Selects how HMCL resolves the Java runtime used to launch a game.
///
/// Additional payload for these modes is stored in `GameSettings`:
/// - [VERSION] uses `javaVersion` as a Java major version string.
/// - [DETECTED] uses `javaVersion` as the detected runtime version and `defaultJavaPath` as the remembered executable path.
/// - [CUSTOM] uses `customJavaPath` as the executable path selected by the user.
///
/// @author Glavo
@NotNullByDefault
public enum JavaVersionType {
    /// Automatically selects a suitable Java runtime for the target game version.
    AUTO,

    /// Selects an installed Java runtime by major version.
    VERSION,

    /// Selects one runtime from the Java runtimes detected by HMCL.
    DETECTED,

    /// Uses a user-provided Java executable path.
    CUSTOM
}
