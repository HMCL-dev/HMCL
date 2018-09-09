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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * If a version string formats x.x.x.x, a {@code IntVersionNumber}
 * will be generated.
 *
 * @author huangyuhui
 */
public final class IntVersionNumber extends VersionNumber {

    final List<Integer> version;

    public static boolean isIntVersionNumber(String version) {
        if (version.chars().noneMatch(ch -> ch != '.' && (ch < '0' || ch > '9'))
                && !version.contains("..") && StringUtils.isNotBlank(version)) {
            String[] arr = version.split("\\.");
            for (String str : arr)
                if (str.length() > 9)
                    // Numbers which are larger than 1e9 cannot be stored as integer.
                    return false;
            return true;
        } else {
            return false;
        }
    }

    IntVersionNumber(String version) {
        if (!isIntVersionNumber(version))
            throw new IllegalArgumentException("The version " + version + " is malformed, only dots and digits are allowed.");

        List<Integer> versions = Arrays.stream(version.split("\\."))
                .map(Integer::parseInt)
                .collect(Collectors.toList());
        while (!versions.isEmpty() && versions.get(versions.size() - 1) == 0)
            versions.remove(versions.size() - 1);

        this.version = versions;
    }

    public int get(int index) {
        return version.get(index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version);
    }

    @Override
    public String toString() {
        return version.stream().map(Object::toString).collect(Collectors.joining("."));
    }
}
