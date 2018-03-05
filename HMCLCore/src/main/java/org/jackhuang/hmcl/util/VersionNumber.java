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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

/**
 * The formatted version number represents a version string.
 *
 * @author huangyuhui
 */
public abstract class VersionNumber implements Comparable<VersionNumber> {

    /**
     * @throws IllegalArgumentException if there are some characters excluding digits and dots.
     * @param version version string in form x.x.x
     * @return the int version number
     */
    public static IntVersionNumber asIntVersionNumber(String version) {
        if (version.chars().filter(ch -> ch != '.' && (ch < '0' || ch > '9')).count() > 0
                || version.contains("..") || StringUtils.isBlank(version))
            throw new IllegalArgumentException("The version " + version + " is malformed, only dots and digits are allowed.");

        String[] s = version.split("\\.");
        ArrayList<Integer> versions = new ArrayList<>(s.length);
        for (String value : s) versions.add(Integer.parseInt(value));
        while (!versions.isEmpty() && versions.get(versions.size() - 1) == 0)
            versions.remove(versions.size() - 1);

        return new IntVersionNumber(Collections.unmodifiableList(versions));
    }

    public static StringVersionNumber asStringVersionNumber(String version) {
        return new StringVersionNumber(version);
    }

    public static VersionNumber asVersion(String version) {
        Objects.requireNonNull(version);
        try {
            return asIntVersionNumber(version);
        } catch (IllegalArgumentException e) {
            return asStringVersionNumber(version);
        }
    }

    public static Optional<String> parseVersion(String str) {
        if (str.chars().anyMatch(it -> it != '.' && (it < '0' || it > '9')) || StringUtils.isBlank(str))
            return Optional.empty();
        String[] s = str.split("\\.");
        for (String i : s)
            if (StringUtils.isBlank(i))
                return Optional.empty();
        StringBuilder builder = new StringBuilder();
        int last = s.length - 1;
        for (int i = s.length - 1; i >= 0; --i)
            if (Integer.parseInt(s[i]) == 0)
                last = i;
        for (int i = 0; i < last; ++i)
            builder.append(s[i]).append(".");
        return Optional.of(builder.append(s[last]).toString());
    }
}
