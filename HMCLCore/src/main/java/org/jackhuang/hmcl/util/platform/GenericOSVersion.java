/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.util.platform.windows.WindowsVersion;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Generic implementation of [OSVersion].
///
/// Note: For Windows version numbers, please use [WindowsVersion].
///
/// @author Glavo
public record GenericOSVersion(@NotNull OperatingSystem os, @NotNull VersionNumber version) implements OSVersion {
    public GenericOSVersion {
        Objects.requireNonNull(os);
        if (os == OperatingSystem.WINDOWS) {
            throw new AssertionError("Please use the " + WindowsVersion.class.getName());
        }
    }

    public GenericOSVersion(OperatingSystem os, String version) {
        this(os, VersionNumber.asVersion(version));
    }

    @Override
    public @NotNull OperatingSystem getOperatingSystem() {
        return os;
    }

    @Override
    public @NotNull String getVersion() {
        return version.toString();
    }

    @Override
    public boolean isAtLeast(@NotNull OSVersion otherVersion) {
        return this == otherVersion || otherVersion instanceof GenericOSVersion that
                && this.os == that.os
                && this.version.compareTo(that.version) >= 0;
    }
}
