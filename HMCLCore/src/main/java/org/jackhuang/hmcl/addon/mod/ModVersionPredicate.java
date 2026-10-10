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

import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Evaluates Maven and extended-SemVer dependency predicates; malformed input fails closed.
@NotNullByDefault
final class ModVersionPredicate {
    /// Utility class; not instantiable.
    private ModVersionPredicate() {
    }

    /// Returns whether a provider version satisfies a loader-specific constraint.
    ///
    /// @param loader the declaring loader
    /// @param constraint the raw constraint
    /// @param version the raw provider version
    /// @return whether the version satisfies the constraint
    static boolean satisfies(
            ModLoaderType loader,
            @Nullable String constraint,
            @Nullable String version) {
        if (constraint == null || constraint.isBlank() || "*".equals(constraint.trim())) {
            return true;
        }
        if (version == null || version.isBlank() || version.contains("${")) {
            return false;
        }
        try {
            String trimmed = constraint.trim();
            if (loader == ModLoaderType.QUILT && isBareSemVer(trimmed)) {
                return matchesCompatibleRange('^', trimmed, version);
            }
            boolean mavenSyntax = trimmed.startsWith("[") || trimmed.startsWith("(");
            boolean semVerSyntax = containsAny(trimmed, '>', '<', '~', '^', '*') || trimmed.contains("||");
            if (mavenSyntax && !semVerSyntax) {
                return satisfiesMaven(trimmed, version);
            }
            if (semVerSyntax && !mavenSyntax) {
                return satisfiesSemVer(trimmed, version);
            }
            return switch (loader) {
                case FORGE, NEO_FORGE, CLEANROOM -> satisfiesMaven(trimmed, version);
                case UNKNOWN -> satisfiesSemVer(trimmed, version) || satisfiesMaven(trimmed, version);
                default -> satisfiesSemVer(trimmed, version);
            };
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /// Compares versions using Maven ComparableVersion-compatible ordering.
    ///
    /// @param left the left version
    /// @param right the right version
    /// @return a negative, zero, or positive comparison result
    static int compareMaven(String left, String right) {
        return VersionNumber.compare(stripV(left.trim()), stripV(right.trim()));
    }

    /// Compares Fabric extended-SemVer versions, falling back to lexical ordering for opaque values.
    ///
    /// @param left the left version
    /// @param right the right version
    /// @return a negative, zero, or positive comparison result
    static int compareSemVer(String left, String right) {
        String normalizedLeft = stripBuild(stripV(left.trim()));
        String normalizedRight = stripBuild(stripV(right.trim()));
        if (!isSemVerLike(normalizedLeft) || !isSemVerLike(normalizedRight)) {
            return normalizedLeft.compareTo(normalizedRight);
        }

        int leftDash = normalizedLeft.indexOf('-');
        int rightDash = normalizedRight.indexOf('-');
        String leftBase = leftDash < 0 ? normalizedLeft : normalizedLeft.substring(0, leftDash);
        String rightBase = rightDash < 0 ? normalizedRight : normalizedRight.substring(0, rightDash);
        int core = compareNumericComponents(leftBase, rightBase);
        if (core != 0) {
            return core;
        }
        if (leftDash < 0 && rightDash < 0) {
            return 0;
        }
        if (leftDash < 0) {
            return 1;
        }
        if (rightDash < 0) {
            return -1;
        }
        return comparePrerelease(normalizedLeft.substring(leftDash + 1), normalizedRight.substring(rightDash + 1));
    }

    /// Evaluates a Maven version range or bare soft-minimum version.
    private static boolean satisfiesMaven(String constraint, String version) {
        for (String interval : splitTopLevel(constraint)) {
            String value = interval.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.charAt(0) != '[' && value.charAt(0) != '(') {
                if (compareMaven(version, value) >= 0) {
                    return true;
                }
                continue;
            }
            if (value.length() < 2 || value.charAt(value.length() - 1) != ']'
                    && value.charAt(value.length() - 1) != ')') {
                continue;
            }

            boolean includeLower = value.charAt(0) == '[';
            boolean includeUpper = value.charAt(value.length() - 1) == ']';
            String body = value.substring(1, value.length() - 1);
            int comma = body.indexOf(',');
            if (comma < 0) {
                String exact = body.trim();
                if (!exact.isEmpty() && compareMaven(version, exact) == 0) {
                    return true;
                }
                continue;
            }

            String lower = body.substring(0, comma).trim();
            String upper = body.substring(comma + 1).trim();
            boolean matches = lower.isEmpty();
            if (!lower.isEmpty()) {
                int comparison = compareMaven(version, lower);
                matches = includeLower ? comparison >= 0 : comparison > 0;
            }
            if (matches && !upper.isEmpty()) {
                int comparison = compareMaven(version, upper);
                matches = includeUpper ? comparison <= 0 : comparison < 0;
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    /// Evaluates Fabric-style OR alternatives containing AND-separated predicate terms.
    private static boolean satisfiesSemVer(String constraint, String version) {
        for (String alternative : constraint.split("\\|\\|")) {
            String trimmed = alternative.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean all = true;
            for (String term : trimmed.split("\\s+")) {
                if (!term.isEmpty() && !termMatches(term, version)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /// Evaluates one Fabric-style predicate term.
    private static boolean termMatches(String term, String version) {
        if ("*".equals(term) || "any".equalsIgnoreCase(term)) {
            return true;
        }
        if (term.charAt(0) == '~' || term.charAt(0) == '^') {
            return matchesCompatibleRange(term.charAt(0), term.substring(1), version);
        }

        int operatorEnd = 0;
        while (operatorEnd < term.length() && "><=".indexOf(term.charAt(operatorEnd)) >= 0) {
            operatorEnd++;
        }
        String operator = term.substring(0, operatorEnd);
        String expected = term.substring(operatorEnd).trim();
        boolean prereleaseFloor = expected.endsWith("-");
        if (prereleaseFloor) {
            expected = expected.substring(0, expected.length() - 1);
        }
        if (expected.isEmpty()) {
            return false;
        }
        if (operator.isEmpty() && isWildcard(expected)) {
            String prefix = expected.substring(0, expected.length() - 2);
            String normalized = stripV(version);
            return normalized.equals(prefix) || normalized.startsWith(prefix + ".");
        }
        if (!isSemVerLike(stripV(expected))) {
            return (operator.isEmpty() || "=".equals(operator) || "==".equals(operator))
                    && version.equals(expected);
        }
        if (prereleaseFloor && (operator.isEmpty() || ">".equals(operator) || ">=".equals(operator))
                && stripV(version).split("-", 2)[0].equalsIgnoreCase(stripV(expected))) {
            return true;
        }

        int comparison = compareSemVer(version, expected);
        return switch (operator) {
            case "", "=", "==" -> comparison == 0;
            case ">=" -> comparison >= 0;
            case ">" -> comparison > 0;
            case "<=" -> comparison <= 0;
            case "<" -> comparison < 0;
            default -> false;
        };
    }

    /// Evaluates tilde and caret compatible ranges.
    private static boolean matchesCompatibleRange(char operator, String baseVersion, String version) {
        if (!isSemVerLike(stripV(baseVersion)) || compareSemVer(version, baseVersion) < 0) {
            return false;
        }
        List<Integer> components = numericComponents(stripV(baseVersion));
        if (components.isEmpty()) {
            return false;
        }
        int incrementIndex;
        if (operator == '~') {
            incrementIndex = components.size() >= 2 ? 1 : 0;
        } else {
            incrementIndex = 0;
            while (incrementIndex < components.size() && components.get(incrementIndex) == 0) {
                incrementIndex++;
            }
            if (incrementIndex >= components.size()) {
                incrementIndex = components.size() - 1;
            }
        }
        StringBuilder upper = new StringBuilder();
        for (int i = 0; i <= incrementIndex; i++) {
            if (i > 0) {
                upper.append('.');
            }
            upper.append(i == incrementIndex ? components.get(i) + 1 : components.get(i));
        }
        return compareSemVer(version, upper.toString()) < 0;
    }

    /// Splits Maven alternatives on commas outside bracketed intervals.
    private static List<String> splitTopLevel(String value) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '[' || current == '(') {
                depth++;
            } else if (current == ']' || current == ')') {
                depth--;
            } else if (current == ',' && depth == 0) {
                result.add(value.substring(start, i));
                start = i + 1;
            }
        }
        result.add(value.substring(start));
        return result;
    }

    /// Returns whether the string contains any supplied character.
    private static boolean containsAny(String value, char... characters) {
        for (char character : characters) {
            if (value.indexOf(character) >= 0) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether an expected version ends with an x/star wildcard component.
    private static boolean isWildcard(String version) {
        String lower = version.toLowerCase(Locale.ROOT);
        return lower.endsWith(".x") || lower.endsWith(".*");
    }

    /// Returns whether Quilt should interpret a bare version as a compatible-major requirement.
    private static boolean isBareSemVer(String constraint) {
        return !constraint.contains(" ")
                && !constraint.contains("||")
                && !containsAny(constraint, '>', '<', '=', '~', '^', '*', 'x', 'X')
                && isSemVerLike(stripV(constraint));
    }

    /// Returns whether a version has a numeric dotted core suitable for SemVer comparison.
    private static boolean isSemVerLike(String version) {
        String core = stripBuild(version).split("-", 2)[0];
        if (core.isEmpty()) {
            return false;
        }
        for (String component : core.split("\\.")) {
            if (component.isEmpty() || !component.chars().allMatch(Character::isDigit)) {
                return false;
            }
        }
        return true;
    }

    /// Compares numeric dotted SemVer cores, padding missing components with zero.
    private static int compareNumericComponents(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            String leftPart = i < leftParts.length ? leftParts[i] : "0";
            String rightPart = i < rightParts.length ? rightParts[i] : "0";
            int comparison = compareNumericIdentifier(leftPart, rightPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    /// Compares SemVer prerelease identifiers.
    private static int comparePrerelease(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int count = Math.min(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            boolean leftNumeric = leftParts[i].chars().allMatch(Character::isDigit);
            boolean rightNumeric = rightParts[i].chars().allMatch(Character::isDigit);
            int comparison;
            if (leftNumeric && rightNumeric) {
                comparison = compareNumericIdentifier(leftParts[i], rightParts[i]);
            } else if (leftNumeric) {
                comparison = -1;
            } else if (rightNumeric) {
                comparison = 1;
            } else {
                comparison = leftParts[i].compareTo(rightParts[i]);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(leftParts.length, rightParts.length);
    }

    /// Compares arbitrarily long numeric identifiers without integer conversion.
    private static int compareNumericIdentifier(String left, String right) {
        String normalizedLeft = left.replaceFirst("^0+(?!$)", "");
        String normalizedRight = right.replaceFirst("^0+(?!$)", "");
        int length = Integer.compare(normalizedLeft.length(), normalizedRight.length());
        return length != 0 ? length : normalizedLeft.compareTo(normalizedRight);
    }

    /// Returns the numeric core components of a SemVer-like version.
    private static List<Integer> numericComponents(String version) {
        String core = stripBuild(version).split("-", 2)[0];
        List<Integer> result = new ArrayList<>();
        for (String component : core.split("\\.")) {
            try {
                result.add(Integer.parseInt(component));
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
        return result;
    }

    /// Removes a SemVer build metadata suffix.
    private static String stripBuild(String version) {
        int plus = version.indexOf('+');
        return plus < 0 ? version : version.substring(0, plus);
    }

    /// Removes a conventional leading v from a numeric version.
    private static String stripV(String version) {
        return version.length() > 1 && (version.charAt(0) == 'v' || version.charAt(0) == 'V')
                && Character.isDigit(version.charAt(1))
                ? version.substring(1)
                : version;
    }
}
