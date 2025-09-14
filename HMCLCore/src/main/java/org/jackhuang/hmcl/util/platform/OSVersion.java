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

import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// @author Glavo
public sealed interface OSVersion {
    OSVersion.Windows WINDOWS_2000 = new Windows(5, 0);
    OSVersion.Windows WINDOWS_XP = new Windows(5, 1);
    OSVersion.Windows WINDOWS_VISTA = new Windows(6, 0);
    OSVersion.Windows WINDOWS_7 = new Windows(6, 1);
    OSVersion.Windows WINDOWS_8 = new Windows(6, 2);
    OSVersion.Windows WINDOWS_8_1 = new Windows(6, 3);
    OSVersion.Windows WINDOWS_10 = new Windows(10, 0);
    OSVersion.Windows WINDOWS_11 = new Windows(10, 0, 22000);

    static OSVersion of(OperatingSystem os, String version) {
        if (Objects.requireNonNull(os) == OperatingSystem.WINDOWS) {
            return OSVersion.Windows.parse(version);
        } else {
            return new Generic(os, VersionNumber.asVersion(version));
        }
    }

    @NotNull OperatingSystem getOperatingSystem();

    @NotNull String getVersion();

    /// Returns `true` if the current version and `otherVersion` have the same [operating system][#getOperatingSystem()]
    /// and the version is not lower than `otherVersion`; otherwise returns `false`.
    ///
    /// For example, if you want to check that the system is Windows and the version is at least Windows 7, you can do this:
    ///
    /// ```java
    /// version.isAtLeast(OSVersion.WINDOWS_7)
    /// ```
    boolean isAtLeast(@NotNull OSVersion otherVersion);

    record Windows(int major, int minor, int build, int revision,
                   String version) implements OSVersion, Comparable<Windows> {
        private static String toVersion(int major, int minor, int build, int revision) {
            StringBuilder builder = new StringBuilder();
            builder.append(major).append('.').append(minor);
            if (build > 0 || revision > 0) {
                builder.append('.').append(build);
                if (revision > 0) {
                    builder.append('.').append(revision);
                }
            }
            return builder.toString();
        }

        public static OSVersion.Windows parse(String version) {
            Matcher matcher = Pattern.compile("^(?<major>\\d+)\\.(?<minor>\\d+)(\\.(?<build>\\d+)(\\.(?<revision>\\d+))?)?")
                    .matcher(version);
            if (matcher.find()) {
                return new Windows(
                        Integer.parseInt(matcher.group("major")),
                        Integer.parseInt(matcher.group("minor")),
                        matcher.group("build") != null ? Integer.parseInt(matcher.group("build")) : 0,
                        matcher.group("revision") != null ? Integer.parseInt(matcher.group("revision")) : 0,
                        version
                );
            } else {
                return new OSVersion.Windows(0, 0, 0, 0, version);
            }
        }

        public Windows(int major, int minor) {
            this(major, minor, 0);
        }

        public Windows(int major, int minor, int build) {
            this(major, minor, build, 0);
        }

        public Windows(int major, int minor, int build, int revision) {
            this(major, minor, build, revision, toVersion(major, minor, build, revision));
        }

        @Override
        public @NotNull OperatingSystem getOperatingSystem() {
            return OperatingSystem.WINDOWS;
        }

        @Override
        public @NotNull String getVersion() {
            return version;
        }

        @Override
        public boolean isAtLeast(@NotNull OSVersion otherVersion) {
            return this == otherVersion || otherVersion instanceof Windows other && this.compareTo(other) >= 0;
        }

        @Override
        public int compareTo(@NotNull OSVersion.Windows that) {
            if (this.major != that.major)
                return Integer.compare(this.major, that.major);
            if (this.minor != that.minor)
                return Integer.compare(this.minor, that.minor);
            if (this.build != that.build)
                return Integer.compare(this.build, that.build);
            return Integer.compare(this.revision, that.revision);
        }

        @Override
        public int hashCode() {
            return Objects.hash(major, minor, build, revision);
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Windows that
                    && this.major == that.major
                    && this.minor == that.minor
                    && this.build == that.build
                    && this.revision == that.revision;
        }

        @Override
        public @NotNull String toString() {
            return version;
        }
    }

    /// Generic implementation of [OSVersion].
    ///
    /// Note: For Windows version numbers, please use [Windows].
    ///
    /// @author Glavo
    record Generic(@NotNull OperatingSystem os, @NotNull VersionNumber version) implements OSVersion {
        public Generic {
            Objects.requireNonNull(os);
            if (os == OperatingSystem.WINDOWS) {
                throw new IllegalArgumentException("Please use the " + Windows.class.getName());
            }
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
            return this == otherVersion || otherVersion instanceof Generic that
                    && this.os == that.os
                    && this.version.compareTo(that.version) >= 0;
        }

        @Override
        public @NotNull String toString() {
            return getVersion();
        }
    }
}

