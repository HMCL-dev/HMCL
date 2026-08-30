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
package org.jackhuang.hmcl.upgrade;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Detects launchers installed and updated by an external system package manager.
///
/// Package-managed installations should not overwrite their own executable
/// files because that would make the system package database disagree with the
/// files installed on disk. The Debian and RPM wrappers set this flag before
/// launching HMCL.
@NotNullByDefault
public final class PackageManagerIntegration {
    /// System property used by package wrappers to mark a package-managed launch.
    public static final String PACKAGE_MANAGED_PROPERTY = "hmcl.package_managed";

    /// Environment variable used by package wrappers to mark a package-managed launch.
    public static final String PACKAGE_MANAGED_ENV = "HMCL_PACKAGE_MANAGED";

    /// Utility class constructor.
    private PackageManagerIntegration() {
    }

    /// Returns whether the current process was launched from a package-managed installation.
    public static boolean isPackageManaged() {
        return isEnabled(System.getProperty(PACKAGE_MANAGED_PROPERTY))
                || isEnabled(System.getenv(PACKAGE_MANAGED_ENV));
    }

    /// Parses a package-managed marker value.
    static boolean isEnabled(@Nullable String value) {
        return value != null
                && !value.isBlank()
                && !"0".equals(value)
                && !"false".equalsIgnoreCase(value)
                && !"no".equalsIgnoreCase(value)
                && !"off".equalsIgnoreCase(value);
    }
}
