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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The formatted version number represents a version string.
 *
 * @author huangyuhui
 */
public abstract class VersionNumber implements Comparable<VersionNumber> {

    public static VersionNumber asVersion(String version) {
        Objects.requireNonNull(version);
        if (ComposedVersionNumber.isComposedVersionNumber(version))
            return new ComposedVersionNumber(version);
        else if (IntVersionNumber.isIntVersionNumber(version))
            return new IntVersionNumber(version);
        else
            return new StringVersionNumber(version);
    }

    public static Optional<String> parseVersion(String str) {
        if (IntVersionNumber.isIntVersionNumber(str))
            return Optional.of(new IntVersionNumber(str).toString());
        else
            return Optional.empty();
    }

    @Override
    public int compareTo(VersionNumber o) {
        return COMPARATOR.compare(this, o);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        else return toString().equals(obj.toString());
    }

    private static <T extends Comparable<T>> int compareTo(List<T> a, List<T> b) {
        int i;
        for (i = 0; i < a.size() && i < b.size(); ++i) {
            int res = a.get(i).compareTo(b.get(i));
            if (res != 0)
                return res;
        }
        if (i < a.size()) return 1;
        else if (i < b.size()) return -1;
        else return 0;
    }

    public static final Comparator<VersionNumber> COMPARATOR = new Comparator<VersionNumber>() {
        @Override
        public int compare(VersionNumber a, VersionNumber b) {
            if (a == null || b == null)
                return 0;
            else {
                if (a instanceof ComposedVersionNumber) {
                    if (b instanceof ComposedVersionNumber)
                        return compareTo(((ComposedVersionNumber) a).composed, ((ComposedVersionNumber) b).composed);
                    else
                        return compare(((ComposedVersionNumber) a).composed.get(0), b);
                } else if (a instanceof IntVersionNumber) {
                    if (b instanceof ComposedVersionNumber)
                        return -compare(b, a);
                    else if (b instanceof IntVersionNumber)
                        return compareTo(((IntVersionNumber) a).version, ((IntVersionNumber) b).version);
                    else if (b instanceof StringVersionNumber)
                        return a.toString().compareTo(b.toString());
                } else if (a instanceof StringVersionNumber) {
                    if (b instanceof ComposedVersionNumber)
                        return -compare(b, a);
                    else if (b instanceof StringVersionNumber)
                        return a.toString().compareTo(b.toString());
                    else if (b instanceof IntVersionNumber)
                        return a.toString().compareTo(b.toString());
                }

                throw new IllegalArgumentException("Unrecognized VersionNumber " + a + " and " + b);
            }
        }

    };
}
