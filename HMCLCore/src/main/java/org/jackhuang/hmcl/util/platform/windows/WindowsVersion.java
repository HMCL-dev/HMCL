
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
package org.jackhuang.hmcl.util.platform.windows;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Glavo
 * @see <a href="https://learn.microsoft.com/windows/win32/sysinfo/operating-system-version">Operating System Version</a>
 */
public final class WindowsVersion implements Comparable<WindowsVersion> {

    public static final WindowsVersion UNKNOWN = new WindowsVersion(0, 0);

    public static final WindowsVersion WINDOWS_2000 = new WindowsVersion(5, 0);
    public static final WindowsVersion WINDOWS_XP = new WindowsVersion(5, 1);
    public static final WindowsVersion WINDOWS_VISTA = new WindowsVersion(6, 0);
    public static final WindowsVersion WINDOWS_7 = new WindowsVersion(6, 1);
    public static final WindowsVersion WINDOWS_8 = new WindowsVersion(6, 2);
    public static final WindowsVersion WINDOWS_8_1 = new WindowsVersion(6, 3);
    public static final WindowsVersion WINDOWS_10 = new WindowsVersion(10, 0);
    public static final WindowsVersion WINDOWS_11 = new WindowsVersion(10, 0, 22000);

    private final int major;
    private final int minor;
    private final int build;
    private final int revision;
    private final String version;

    public WindowsVersion(int major, int minor) {
        this(major, minor, 0);
    }

    public WindowsVersion(int major, int minor, int build) {
        this(major, minor, build, 0);
    }

    public WindowsVersion(int major, int minor, int build, int revision) {
        this.major = major;
        this.minor = minor;
        this.build = build;
        this.revision = revision;

        StringBuilder builder = new StringBuilder();
        builder.append(major).append('.').append(minor);
        if (build > 0 || revision > 0) {
            builder.append('.').append(build);
            if (revision > 0) {
                builder.append('.').append(revision);
            }
        }
        this.version = builder.toString();
    }

    public WindowsVersion(@NotNull String version) {
        this.version = Objects.requireNonNull(version);

        Matcher matcher = Pattern.compile("^(?<major>\\d+)\\.(?<minor>\\d+)(\\.(?<build>\\d+)(\\.(?<revision>\\d+))?)?")
                .matcher(version);
        if (matcher.find()) {
            this.major = Integer.parseInt(matcher.group("major"));
            this.minor = Integer.parseInt(matcher.group("minor"));
            this.build = matcher.group("build") != null ? Integer.parseInt(matcher.group("build")) : 0;
            this.revision = matcher.group("revision") != null ? Integer.parseInt(matcher.group("revision")) : 0;
        } else {
            this.major = 0;
            this.minor = 0;
            this.build = 0;
            this.revision = 0;
        }
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getBuild() {
        return build;
    }

    public int getRevision() {
        return revision;
    }

    @Override
    public int compareTo(@NotNull WindowsVersion that) {
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
        if (this == o)
            return true;
        if (!(o instanceof WindowsVersion))
            return false;
        WindowsVersion that = (WindowsVersion) o;

        return this.major == that.major &&
                this.minor == that.minor &&
                this.build == that.build &&
                this.revision == that.revision;
    }

    @Override
    public String toString() {
        return version;
    }
}
