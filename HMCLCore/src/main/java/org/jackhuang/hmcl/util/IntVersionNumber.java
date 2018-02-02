/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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

import java.util.List;
import java.util.stream.Collectors;

/**
 * If a version string formats x.x.x.x, a {@code IntVersionNumber}
 * will be generated.
 *
 * @author huangyuhui
 */
public final class IntVersionNumber extends VersionNumber {

    private final List<Integer> version;

    IntVersionNumber(List<Integer> version) {
        this.version = version;
    }

    public int get(int index) {
        return version.get(index);
    }

    @Override
    public int compareTo(VersionNumber o) {
        if (!(o instanceof IntVersionNumber))
            return 0;
        IntVersionNumber other = (IntVersionNumber) o;
        int len = Math.min(this.version.size(), other.version.size());
        for (int i = 0; i < len; ++i)
            if (!version.get(i).equals(other.version.get(i)))
                return version.get(i).compareTo(other.version.get(i));
        return Integer.compare(this.version.size(), other.version.size());
    }

    @Override
    public int hashCode() {
        int hash = 3;
        for (int i : version)
            hash = 83 * hash + i;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof VersionNumber))
            return false;
        if (!(obj instanceof IntVersionNumber))
            return true;
        IntVersionNumber other = (IntVersionNumber) obj;
        if (version.size() != other.version.size())
            return false;

        for (int i = 0; i < version.size(); ++i)
            if (!version.get(i).equals(other.version.get(i)))
                return false;
        return true;
    }

    @Override
    public String toString() {
        return String.join(".", version.stream().map(Object::toString).collect(Collectors.toList()));
    }
}
