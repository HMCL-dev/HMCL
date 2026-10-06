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
package org.jackhuang.hmcl.addon.mod;

import org.jackhuang.hmcl.addon.mod.NestedJarInspector.NestedJar;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Evaluates whether a Minecraft version satisfies a mod's declared Minecraft version constraint.
///
/// Loader-native predicates are tried first, with {@link GameVersionNumber} as a fallback for
/// snapshots and legacy Minecraft version names.
@NotNullByDefault
public final class MinecraftVersionMatcher {
    /// Utility class; not instantiable.
    private MinecraftVersionMatcher() {
    }

    /// Whether a bundled copy is the one a multi-version wrapper would load for {@code instanceVersion}
    /// — either an exact build for it, or one whose declared version range covers it.
    public static boolean matches(NestedJar node, @Nullable String instanceVersion) {
        return matchesExact(node, instanceVersion)
                || satisfies(node.loaderType(), node.minecraftVersion(), instanceVersion);
    }

    /// Returns whether the file name or declared version contains the exact instance version token.
    public static boolean matchesExact(NestedJar node, @Nullable String instanceVersion) {
        if (instanceVersion == null || instanceVersion.isBlank())
            return false;
        return containsVersionToken(node.fileName(), instanceVersion)
                || containsVersionToken(node.version(), instanceVersion);
    }

    /// Returns whether a string contains a complete version token rather than a dotted prefix.
    private static boolean containsVersionToken(@Nullable String haystack, String version) {
        if (haystack == null)
            return false;
        int i = haystack.indexOf(version);
        while (i >= 0) {
            char before = i > 0 ? haystack.charAt(i - 1) : ' ';
            int end = i + version.length();
            char after = end < haystack.length() ? haystack.charAt(end) : ' ';
            boolean leadingOk = before != '.' && !Character.isDigit(before);
            boolean trailingOk;
            if (Character.isDigit(after)) {
                trailingOk = false;
            } else if (after == '.') {
                char next = end + 1 < haystack.length() ? haystack.charAt(end + 1) : ' ';
                trailingOk = !Character.isDigit(next);
            } else {
                trailingOk = true;
            }
            if (leadingOk && trailingOk)
                return true;
            i = haystack.indexOf(version, i + 1);
        }
        return false;
    }

    /// Returns whether an instance version satisfies a loader-specific Minecraft constraint.
    public static boolean satisfies(
            ModLoaderType loader,
            @Nullable String constraint,
            @Nullable String version) {
        if (constraint == null || constraint.isBlank() || version == null || version.isBlank())
            return false;
        try {
            if (ModVersionPredicate.satisfies(loader, constraint, version))
                return true;
            GameVersionNumber v = GameVersionNumber.asGameVersion(version);
            return switch (loader) {
                case FORGE, NEO_FORGE -> satisfiesMaven(constraint.trim(), v);
                default -> satisfiesSemVer(constraint, v); // Fabric, Quilt, LegacyFabric, …
            };
        } catch (Exception e) {
            return false;
        }
    }

    /// Evaluates Maven ranges using Minecraft-aware version ordering.
    private static boolean satisfiesMaven(String constraint, GameVersionNumber v) {
        for (String interval : splitTopLevel(constraint)) {
            String s = interval.trim();
            if (s.isEmpty())
                continue;

            if (s.charAt(0) != '[' && s.charAt(0) != '(') {
                if (GameVersionNumber.isKnown(s) && v.compareTo(GameVersionNumber.asGameVersion(s)) >= 0)
                    return true;
                continue;
            }

            boolean incLo = s.charAt(0) == '[';
            boolean incHi = s.charAt(s.length() - 1) == ']';
            String body = s.substring(1, s.length() - 1);
            int comma = body.indexOf(',');
            if (comma < 0) {
                String only = body.trim();
                if (!only.isEmpty() && GameVersionNumber.isKnown(only) && v.compareTo(GameVersionNumber.asGameVersion(only)) == 0)
                    return true;
                continue;
            }

            String loStr = body.substring(0, comma).trim();
            String hiStr = body.substring(comma + 1).trim();
            if ((!loStr.isEmpty() && !GameVersionNumber.isKnown(loStr))
                    || (!hiStr.isEmpty() && !GameVersionNumber.isKnown(hiStr)))
                continue;
            boolean ok = true;
            if (!loStr.isEmpty()) {
                int c = v.compareTo(GameVersionNumber.asGameVersion(loStr));
                ok = incLo ? c >= 0 : c > 0;
            }
            if (ok && !hiStr.isEmpty()) {
                int c = v.compareTo(GameVersionNumber.asGameVersion(hiStr));
                ok = incHi ? c <= 0 : c < 0;
            }
            if (ok)
                return true;
        }
        return false;
    }

    /// Splits on commas that are outside any bracket group (Maven separates multiple intervals by
    /// comma, but commas also appear inside a single interval's brackets).
    private static List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '[' || ch == '(')
                depth++;
            else if (ch == ']' || ch == ')')
                depth--;
            else if (ch == ',' && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    /// Evaluates Fabric-style OR alternatives and AND terms.
    private static boolean satisfiesSemVer(String constraint, GameVersionNumber v) {
        for (String alternative : constraint.split("\\|\\|")) {
            String alt = alternative.trim();
            if (alt.isEmpty())
                continue;
            boolean all = true;
            for (String term : alt.split("\\s+")) {
                if (term.isEmpty())
                    continue;
                if (!termMatches(term, v)) {
                    all = false;
                    break;
                }
            }
            if (all)
                return true;
        }
        return false;
    }

    /// Evaluates one Fabric-style version term.
    private static boolean termMatches(String term, GameVersionNumber v) {
        if (term.equals("*") || term.equalsIgnoreCase("any"))
            return true;

        int i = 0;
        while (i < term.length() && "><=".indexOf(term.charAt(i)) >= 0)
            i++;
        String op = term.substring(0, i);
        String ver = term.substring(i).trim();
        if (ver.endsWith("-"))
            ver = ver.substring(0, ver.length() - 1);
        if (ver.isEmpty())
            return false;

        if (op.isEmpty() && (ver.endsWith(".x") || ver.endsWith(".X") || ver.endsWith(".*"))) {
            String prefix = ver.substring(0, ver.length() - 2);
            String vs = v.toString();
            return vs.equals(prefix) || vs.startsWith(prefix + ".");
        }

        if (!GameVersionNumber.isKnown(ver))
            return false;
        int c = v.compareTo(GameVersionNumber.asGameVersion(ver));
        return switch (op) {
            case "", "=", "==" -> c == 0;
            case ">=" -> c >= 0;
            case ">" -> c > 0;
            case "<=" -> c <= 0;
            case "<" -> c < 0;
            default -> false;
        };
    }
}
