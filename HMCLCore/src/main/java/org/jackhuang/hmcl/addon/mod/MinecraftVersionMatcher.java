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

import java.util.ArrayList;
import java.util.List;

/// Evaluates whether a Minecraft version satisfies a mod's declared Minecraft version constraint.
///
/// A multi-version "wrapper" jar bundles one copy of the same mod per game version, each declaring the
/// range it targets; the wrapper loads whichever copy's range contains the running version. This
/// mirrors that selection so the mod list can highlight the copy an instance would actually use even
/// when the instance version isn't an exact bundled build (it falls inside a build's range).
///
/// Two constraint dialects are handled, one per loader family:
///  - Forge / NeoForge — Maven version ranges: {@code [a,b]}, {@code [a,b)}, {@code (a,b)}, {@code [a,)},
///    {@code (,b]}, {@code [a]} (exact), or a bare {@code a} (soft minimum, i.e. {@code >=a}). Multiple
///    comma-separated top-level intervals are OR-ed.
///  - Fabric / Quilt — SemVer-style predicates: space = AND, {@code ||} = OR, operators
///    {@code >= > <= < =}, and {@code x}/{@code *} wildcards. Fabric's trailing {@code -} (a
///    pre-release marker) is tolerated.
///
/// All comparisons go through {@link GameVersionNumber} so Minecraft's non-SemVer versions (snapshots,
/// {@code 1.20} vs {@code 1.20.1}) order correctly. Anything unparseable yields {@code false} — a
/// missing highlight is far better than a wrong one.
public final class MinecraftVersionMatcher {
    private MinecraftVersionMatcher() {
    }

    /// Whether a bundled copy is the one a multi-version wrapper would load for {@code instanceVersion}
    /// — either an exact build for it, or one whose declared version range covers it.
    public static boolean matches(NestedJar node, String instanceVersion) {
        return matchesExact(node, instanceVersion)
                || satisfies(node.loaderType(), node.minecraftVersion(), instanceVersion);
    }

    /// Whether a bundled copy is an exact build for {@code instanceVersion}: the version appears as a
    /// whole token in the copy's file name or declared version (so "1.21" won't match "1.21.2"). The
    /// declared MC *constraint* is deliberately NOT searched — it is a range, and finding the version
    /// text inside it (e.g. {@code 1.21} in {@code [1.20,1.21)} or {@code <1.21}) would wrongly count
    /// an excluded endpoint as an exact match. Range membership is judged by {@link #satisfies}.
    public static boolean matchesExact(NestedJar node, String instanceVersion) {
        if (instanceVersion == null || instanceVersion.isBlank())
            return false;
        return containsVersionToken(node.fileName(), instanceVersion)
                || containsVersionToken(node.version(), instanceVersion);
    }

    public static boolean containsVersionToken(String haystack, String version) {
        if (haystack == null)
            return false;
        int i = haystack.indexOf(version);
        while (i >= 0) {
            int end = i + version.length();
            char after = end < haystack.length() ? haystack.charAt(end) : ' ';
            if (after != '.' && !Character.isDigit(after)) // not a prefix of a longer version
                return true;
            i = haystack.indexOf(version, i + 1);
        }
        return false;
    }

    public static boolean satisfies(ModLoaderType loader, String constraint, String version) {
        if (constraint == null || constraint.isBlank() || version == null || version.isBlank())
            return false;
        try {
            GameVersionNumber v = GameVersionNumber.asGameVersion(version);
            return switch (loader) {
                case FORGE, NEO_FORGE -> satisfiesMaven(constraint.trim(), v);
                default -> satisfiesSemVer(constraint, v); // Fabric, Quilt, LegacyFabric, …
            };
        } catch (Exception e) {
            return false;
        }
    }

    // ── Maven ranges (Forge / NeoForge) ───────────────────────────────
    private static boolean satisfiesMaven(String constraint, GameVersionNumber v) {
        for (String interval : splitTopLevel(constraint)) {
            String s = interval.trim();
            if (s.isEmpty())
                continue;

            if (s.charAt(0) != '[' && s.charAt(0) != '(') {
                // A bare version is a soft minimum requirement in Maven / Forge.
                if (v.compareTo(GameVersionNumber.asGameVersion(s)) >= 0)
                    return true;
                continue;
            }

            boolean incLo = s.charAt(0) == '[';
            boolean incHi = s.charAt(s.length() - 1) == ']';
            String body = s.substring(1, s.length() - 1);
            int comma = body.indexOf(',');
            if (comma < 0) {
                String only = body.trim(); // [a] — exact
                if (!only.isEmpty() && v.compareTo(GameVersionNumber.asGameVersion(only)) == 0)
                    return true;
                continue;
            }

            String loStr = body.substring(0, comma).trim();
            String hiStr = body.substring(comma + 1).trim();
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

    // ── SemVer-style predicates (Fabric / Quilt) ──────────────────────
    private static boolean satisfiesSemVer(String constraint, GameVersionNumber v) {
        for (String alternative : constraint.split("\\|\\|")) { // OR
            String alt = alternative.trim();
            if (alt.isEmpty())
                continue;
            boolean all = true;
            for (String term : alt.split("\\s+")) { // AND
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

    private static boolean termMatches(String term, GameVersionNumber v) {
        if (term.equals("*") || term.equalsIgnoreCase("any"))
            return true;

        int i = 0;
        while (i < term.length() && "><=".indexOf(term.charAt(i)) >= 0)
            i++;
        String op = term.substring(0, i);
        String ver = term.substring(i).trim();
        if (ver.endsWith("-")) // Fabric pre-release marker, e.g. ">=26.1-"
            ver = ver.substring(0, ver.length() - 1);
        if (ver.isEmpty())
            return false;

        // Wildcards like 1.20.x / 1.20.* only make sense without an operator.
        if (op.isEmpty() && (ver.endsWith(".x") || ver.endsWith(".X") || ver.endsWith(".*"))) {
            String prefix = ver.substring(0, ver.length() - 2);
            String vs = v.toString();
            return vs.equals(prefix) || vs.startsWith(prefix + ".");
        }

        int c = v.compareTo(GameVersionNumber.asGameVersion(ver));
        return switch (op) {
            case "", "=", "==" -> c == 0; // bare version is an exact match in Fabric
            case ">=" -> c >= 0;
            case ">" -> c > 0;
            case "<=" -> c <= 0;
            case "<" -> c < 0;
            default -> false; // ~, ^ and anything unusual: don't guess
        };
    }
}
