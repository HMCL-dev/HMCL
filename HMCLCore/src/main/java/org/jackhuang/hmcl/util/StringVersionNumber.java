/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util;

import java.util.Objects;

/**
 * If a version string contains alphabets, a {@code StringVersionNumber}
 * will be constructed.
 *
 * @author huangyuhui
 */
public final class StringVersionNumber extends VersionNumber {

    private final String version;

    StringVersionNumber(String version) {
        Objects.requireNonNull(version);
        this.version = version;
    }

    @Override
    public int compareTo(VersionNumber o) {
        if (!(o instanceof StringVersionNumber))
            return 0;

        return version.compareTo(((StringVersionNumber) o).version);
    }

    public String getVersion() {
        return version;
    }

    @Override
    public int hashCode() {
        return version.hashCode();
    }

    @Override
    public String toString() {
        return version;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof VersionNumber))
            return false;

        if (!(obj instanceof StringVersionNumber))
            return true;

        return version.equals(((StringVersionNumber) obj).version);
    }

}
