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
package org.jackhuang.hmcl.util.versioning;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@NotNullByDefault
public final class MavenVersionRange {
    public static MavenVersionRange parse(String range) {
        if (range.isBlank()) throw new IllegalArgumentException("Version range is empty");

        List<Restriction> restrictions = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= range.length(); i++) {
            if (i == range.length() || range.charAt(i) == '|') {
                restrictions.add(parseRestriction(range.substring(start, i).trim()));
                start = i + 1;
            }
        }

        return new MavenVersionRange(restrictions);
    }

    private static Restriction parseRestriction(String text) {
        if (text.isEmpty()) throw new IllegalArgumentException("Version range contains an empty restriction");

        char first = text.charAt(0);
        if (first != '[' && first != '(') {
            return new Restriction(VersionNumber.asVersion(text));
        }

        char last = text.charAt(text.length() - 1);
        if ((last != ']' && last != ')') || text.length() < 3) {
            throw new IllegalArgumentException("Version range is missing a closing bracket: " + text);
        }

        String[] bounds = text.substring(1, text.length() - 1).split(",", -1);
        if (bounds.length > 2) {
            throw new IllegalArgumentException("Version range contains too many bounds: " + text);
        }

        boolean lowerInclusive = first == '[';
        boolean upperInclusive = last == ']';

        @Nullable VersionNumber lower = bounds[0].isBlank() ? null : VersionNumber.asVersion(bounds[0].trim());
        @Nullable VersionNumber upper = bounds.length == 2 && !bounds[1].isBlank() ? VersionNumber.asVersion(bounds[1].trim()) : null;

        if (lower != null && upper != null && isEmptyInterval(lower, lowerInclusive, upper, upperInclusive)) {
            throw new IllegalArgumentException("Version range is empty: " + text);
        }

        return new Restriction(null, lower, lowerInclusive, upper, upperInclusive);
    }

    private static boolean isEmptyInterval(VersionNumber lower, boolean lowerInclusive, VersionNumber upper, boolean upperInclusive) {
        int result = lower.compareTo(upper);
        if (result > 0) return true;

        return result == 0 && !lowerInclusive && !upperInclusive;
    }

    private final List<Restriction> restrictions;

    private MavenVersionRange(List<Restriction> restrictions) {
        this.restrictions = List.copyOf(restrictions);
    }

    public boolean contains(String version) {
        VersionNumber number = VersionNumber.asVersion(version);
        for (Restriction restriction : restrictions) {
            if (restriction.contains(number)) return true;
        }
        return false;
    }

    public boolean isSoft() {
        return restrictions.size() == 1 && restrictions.get(0).softVersion != null;
    }

    private record Restriction(@Nullable VersionNumber softVersion, @Nullable VersionNumber lower,
                               boolean lowerInclusive, @Nullable VersionNumber upper, boolean upperInclusive) {
        Restriction(VersionNumber version) {
            this(version, null, true, null, true);
        }

        boolean contains(VersionNumber version) {
            if (softVersion != null) return softVersion.compareTo(version) == 0;

            if (lower != null) {
                int result = lower.compareTo(version);
                if (result > 0 || result == 0 && !lowerInclusive) return false;
            }
            if (upper != null) {
                int result = upper.compareTo(version);
                return result >= 0 && (result != 0 || upperInclusive);
            }
            return true;
        }
    }
}
