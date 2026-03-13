/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author huangyuhui
 */
public final class StringUtils {

    private StringUtils() {
    }

    public static String getStackTrace(Throwable throwable) {
        StringWriter stringWriter = new StringWriter(512);
        try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
            throwable.printStackTrace(printWriter);
        }
        return stringWriter.toString();
    }

    public static String getStackTrace(StackTraceElement[] elements) {
        StringBuilder builder = new StringBuilder();
        for (StackTraceElement element : elements)
            builder.append("\tat ").append(element).append(System.lineSeparator());
        return builder.toString();
    }

    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static String normalizeWhitespaces(String str) {
        if (str == null)
            return "";

        int start = 0;
        int end = str.length();

        while (start < str.length() && Character.isWhitespace(str.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(str.charAt(end - 1))) {
            end--;
        }

        if (end == start) {
            return "";
        }

        StringBuilder builder = null;

        int i = start;
        while (i < end) {
            char ch = str.charAt(i);
            if (Character.isWhitespace(ch)) {
                int whitespaceEnd = i + 1;
                while (whitespaceEnd < end && Character.isWhitespace(str.charAt(whitespaceEnd))) {
                    whitespaceEnd++;
                }

                if (whitespaceEnd - i > 1 || ch != ' ') {
                    if (builder == null) {
                        builder = new StringBuilder(end - start);
                        builder.append(str, start, i);
                    }
                    builder.append(' ');
                    i = whitespaceEnd;
                    continue;
                }
            }

            if (builder != null) {
                builder.append(ch);
            }
            i++;
        }

        return builder != null ? builder.toString() : str.substring(start, end);
    }

    public static String capitalizeFirst(String str) {
        if (str == null || str.isEmpty())
            return str;

        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    public static String substringBeforeLast(String str, char delimiter) {
        return substringBeforeLast(str, delimiter, str);
    }

    public static String substringBeforeLast(String str, char delimiter, String missingDelimiterValue) {
        int index = str.lastIndexOf(delimiter);
        return index == -1 ? missingDelimiterValue : str.substring(0, index);
    }

    public static String substringBeforeLast(String str, String delimiter) {
        return substringBeforeLast(str, delimiter, str);
    }

    public static String substringBeforeLast(String str, String delimiter, String missingDelimiterValue) {
        int index = str.lastIndexOf(delimiter);
        return index == -1 ? missingDelimiterValue : str.substring(0, index);
    }

    public static String substringBefore(String str, char delimiter) {
        return substringBefore(str, delimiter, str);
    }

    public static String substringBefore(String str, char delimiter, String missingDelimiterValue) {
        int index = str.indexOf(delimiter);
        return index == -1 ? missingDelimiterValue : str.substring(0, index);
    }

    public static String substringBefore(String str, String delimiter) {
        return substringBefore(str, delimiter, str);
    }

    public static String substringBefore(String str, String delimiter, String missingDelimiterValue) {
        int index = str.indexOf(delimiter);
        return index == -1 ? missingDelimiterValue : str.substring(0, index);
    }

    public static String substringAfterLast(String str, char delimiter) {
        return substringAfterLast(str, delimiter, "");
    }

    public static String substringAfterLast(String str, char delimiter, String missingDelimiterValue) {
        int index = str.lastIndexOf(delimiter);
        return index == -1 ? missingDelimiterValue : str.substring(index + 1);
    }

    public static String substringAfterLast(String str, String delimiter) {
        return substringAfterLast(str, delimiter, "");
    }

    public static String substringAfterLast(String str, String delimiter, String missingDelimiterValue) {
        int index = str.lastIndexOf(delimiter);
        return index == -1 ? missingDelimiterValue : str.substring(index + delimiter.length());
    }

    public static String substringAfter(String str, char delimiter) {
        return substringAfter(str, delimiter, "");
    }

    public static String substringAfter(String str, char delimiter, String missingDelimiterValue) {
        int index = str.indexOf(delimiter);
        return index == -1 ? missingDelimiterValue : str.substring(index + 1);
    }

    public static String substringAfter(String str, String delimiter) {
        return substringAfter(str, delimiter, "");
    }

    public static String substringAfter(String str, String delimiter, String missingDelimiterValue) {
        int index = str.indexOf(delimiter);
        return index == -1 ? missingDelimiterValue : str.substring(index + delimiter.length());
    }

    public static boolean isSurrounded(String str, String prefix, String suffix) {
        return str.startsWith(prefix) && str.endsWith(suffix);
    }

    public static String removeSurrounding(String str, String delimiter) {
        return removeSurrounding(str, delimiter, delimiter);
    }

    public static String removeSurrounding(String str, String prefix, String suffix) {
        if ((str.length() >= prefix.length() + suffix.length()) && str.startsWith(prefix) && str.endsWith(suffix))
            return str.substring(prefix.length(), str.length() - suffix.length());
        else
            return str;
    }

    public static String addPrefix(String str, String prefix) {
        if (str.startsWith(prefix))
            return str;
        else
            return prefix + str;
    }

    public static String addSuffix(String str, String suffix) {
        if (str.endsWith(suffix))
            return str;
        else
            return str + suffix;
    }

    public static String removePrefix(String str, String prefix) {
        return str.startsWith(prefix) ? str.substring(prefix.length()) : str;
    }

    public static String removePrefix(String str, String... prefixes) {
        for (String prefix : prefixes)
            if (str.startsWith(prefix))
                return str.substring(prefix.length());
        return str;
    }

    public static String removeSuffix(String str, String suffix) {
        return str.endsWith(suffix) ? str.substring(0, str.length() - suffix.length()) : str;
    }

    /**
     * Remove one suffix of the suffixes of the string.
     */
    public static String removeSuffix(String str, String... suffixes) {
        for (String suffix : suffixes)
            if (str.endsWith(suffix))
                return str.substring(0, str.length() - suffix.length());
        return str;
    }

    public static boolean containsOne(Collection<String> patterns, String... targets) {
        for (String pattern : patterns) {
            String lowerPattern = pattern.toLowerCase(Locale.ROOT);
            for (String target : targets)
                if (lowerPattern.contains(target.toLowerCase(Locale.ROOT)))
                    return true;
        }
        return false;
    }

    public static boolean containsOne(String pattern, String... targets) {
        String lowerPattern = pattern.toLowerCase(Locale.ROOT);
        for (String target : targets)
            if (lowerPattern.contains(target.toLowerCase(Locale.ROOT)))
                return true;
        return false;
    }

    public static boolean containsChinese(String str) {
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (ch >= '\u4e00' && ch <= '\u9fa5')
                return true;
        }
        return false;
    }

    public static boolean containsEmoji(String str) {
        for (int i = 0; i < str.length(); ) {
            int ch = str.codePointAt(i);

            if (ch >= 0x1F300 && ch <= 0x1FAFF)
                return true;

            i += Character.charCount(ch);
        }

        return false;
    }

    /// Check if the code point is a full-width character.
    public static boolean isFullWidth(int codePoint) {
        return codePoint >= '\uff10' && codePoint <= '\uff19'       // full-width digits
                || codePoint >= '\uff21' && codePoint <= '\uff3a'   // full-width uppercase letters
                || codePoint >= '\uff41' && codePoint <= '\uff5a'   // full-width lowercase letters
                || codePoint == '\uff08'                            // full-width left parenthesis
                || codePoint == '\uff09'                            // full-width right parenthesis
                || codePoint == '\uff0c'                            // full-width comma
                || codePoint == '\uff05'                            // full-width percent sign
                || codePoint == '\uff0e'                            // full-width period
                || codePoint == '\u3000'                            // full-width ideographic space
                || codePoint == '\uff03';                           // full-width number sign
    }

    /// Convert full-width characters to half-width characters.
    public static String toHalfWidth(String str) {
        int i = 0;
        while (i < str.length()) {
            int cp = str.codePointAt(i);

            if (isFullWidth(cp)) {
                break;
            }

            i += Character.charCount(cp);
        }

        if (i == str.length())
            return str;

        var builder = new StringBuilder(str.length());
        builder.append(str, 0, i);
        while (i < str.length()) {
            int c = str.codePointAt(i);

            if (c >= '\uff10' && c <= '\uff19') builder.append((char) (c - 0xfee0));
            else if (c >= '\uff21' && c <= '\uff3a') builder.append((char) (c - 0xfee0));
            else if (c >= '\uff41' && c <= '\uff5a') builder.append((char) (c - 0xfee0));
            else if (c == '\uff08') builder.append('(');
            else if (c == '\uff09') builder.append(')');
            else if (c == '\uff0c') builder.append(',');
            else if (c == '\uff05') builder.append('%');
            else if (c == '\uff0e') builder.append('.');
            else if (c == '\u3000') builder.append(' ');
            else if (c == '\uff03') builder.append('#');
            else builder.appendCodePoint(c);

            i += Character.charCount(c);
        }
        return builder.toString();
    }

    private static boolean isVarNameStart(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_';
    }

    private static boolean isVarNamePart(char ch) {
        return isVarNameStart(ch) || (ch >= '0' && ch <= '9');
    }

    private static int findVarEnd(String str, int offset) {
        if (offset < str.length() - 1 && isVarNameStart(str.charAt(offset))) {
            int end = offset + 1;
            while (end < str.length()) {
                if (!isVarNamePart(str.charAt(end))) {
                    break;
                }
                end++;
            }
            return end;
        }

        return -1;
    }

    public static List<String> tokenize(String str) {
        return tokenize(str, null);
    }

    public static List<String> tokenize(String str, Map<String, String> vars) {
        if (isBlank(str)) {
            return new ArrayList<>();
        }

        if (vars == null) {
            vars = Collections.emptyMap();
        }

        // Split the string with ' and space cleverly.
        ArrayList<String> parts = new ArrayList<>();
        int varEnd;

        boolean hasValue = false;
        StringBuilder current = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); ) {
            char c = str.charAt(i);
            if (c == '\'') {
                hasValue = true;
                int end = str.indexOf(c, i + 1);
                if (end < 0) {
                    end = str.length();
                }
                current.append(str, i + 1, end);
                i = end + 1;

            } else if (c == '"') {
                hasValue = true;
                i++;
                while (i < str.length()) {
                    c = str.charAt(i++);
                    if (c == '"') {
                        break;
                    } else if (c == '`' && i < str.length()) {
                        c = str.charAt(i++);
                        switch (c) {
                            case 'a':
                                c = '\u0007';
                                break;
                            case 'b':
                                c = '\b';
                                break;
                            case 'f':
                                c = '\f';
                                break;
                            case 'n':
                                c = '\n';
                                break;
                            case 'r':
                                c = '\r';
                                break;
                            case 't':
                                c = '\t';
                                break;
                            case 'v':
                                c = '\u000b';
                                break;
                        }
                        current.append(c);
                    } else if (c == '$' && (varEnd = findVarEnd(str, i)) >= 0) {
                        String key = str.substring(i, varEnd);
                        String value = vars.get(key);
                        if (value != null) {
                            current.append(value);
                        } else {
                            current.append('$').append(key);
                        }

                        i = varEnd;
                    } else {
                        current.append(c);
                    }
                }
            } else if (c == ' ') {
                if (hasValue) {
                    parts.add(current.toString());
                    current.setLength(0);
                    hasValue = false;
                }
                i++;
            } else if (c == '$' && (varEnd = findVarEnd(str, i + 1)) >= 0) {
                hasValue = true;
                String key = str.substring(i + 1, varEnd);
                String value = vars.get(key);
                if (value != null) {
                    current.append(value);
                } else {
                    current.append('$').append(key);
                }

                i = varEnd;
            } else {
                hasValue = true;
                current.append(c);
                i++;
            }
        }
        if (hasValue) {
            parts.add(current.toString());
        }

        return parts;
    }

    public static String parseColorEscapes(String original) {
        if (original.indexOf('\u00A7') < 0)
            return original;

        return original.replaceAll("\u00A7[0-9a-fk-or]", "");
    }

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("\u00A7([0-9a-fk-or])");
    private static final String FORMAT_CODE = "format_code";

    public static List<Pair<String, String>> parseMinecraftColorCodes(String original) {
        List<Pair<String, String>> pairs = new ArrayList<>();
        if (isBlank(original)) {
            return pairs;
        }
        Matcher matcher = COLOR_CODE_PATTERN.matcher(original);
        String currentColor = "";
        int lastIndex = 0;

        while (matcher.find()) {
            String text = original.substring(lastIndex, matcher.start());
            if (!text.isEmpty()) {
                pairs.add(new Pair<>(text, currentColor));
            }

            char code = matcher.group(1).charAt(0);
            String newColor = switch (code) {
                case '0' -> "black";
                case '1' -> "dark_blue";
                case '2' -> "dark_green";
                case '3' -> "dark_aqua";
                case '4' -> "dark_red";
                case '5' -> "dark_purple";
                case '6' -> "gold";
                case '7' -> "gray";
                case '8' -> "dark_gray";
                case '9' -> "blue";
                case 'a' -> "green";
                case 'b' -> "aqua";
                case 'c' -> "red";
                case 'd' -> "light_purple";
                case 'e' -> "yellow";
                case 'f' -> "white";
                case 'k', 'l', 'm', 'n', 'o' -> FORMAT_CODE;
                case 'r' -> "";
                default -> null;
            };

            if (newColor != null && !newColor.equals(FORMAT_CODE)) {
                currentColor = newColor;
            }

            lastIndex = matcher.end();
        }

        if (lastIndex < original.length()) {
            String remainingText = original.substring(lastIndex);
            pairs.add(new Pair<>(remainingText, currentColor));
        }
        return pairs;
    }

    public static String parseEscapeSequence(String str) {
        int idx = str.indexOf('\033');
        if (idx < 0)
            return str;

        StringBuilder builder = new StringBuilder(str.length());
        boolean inEscape = false;

        builder.append(str, 0, idx);
        for (int i = idx; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (ch == '\033') {
                inEscape = true;
            }
            if (!inEscape) {
                builder.append(ch);
            }
            if (inEscape && ch == 'm') {
                inEscape = false;
            }
        }
        return builder.toString();
    }

    public static String escapeXmlAttribute(String str) {
        return str
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&apos;");
    }

    public static String repeats(char ch, int repeat) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < repeat; i++) {
            result.append(ch);
        }
        return result.toString();
    }

    public static String truncate(String str, int limit) {
        assert limit > 5;

        if (str.length() <= limit) {
            return str;
        }

        final int halfLength = (limit - 5) / 2;
        return str.substring(0, halfLength) + " ... " + str.substring(str.length() - halfLength);
    }

    public static boolean isASCII(String cs) {
        for (int i = 0; i < cs.length(); i++)
            if (cs.charAt(i) >= 128)
                return false;
        return true;
    }

    public static boolean isAlphabeticOrNumber(String str) {
        int length = str.length();
        for (int i = 0; i < length; i++) {
            char ch = str.charAt(i);
            if (!(ch >= '0' && ch <= '9') && !(ch >= 'a' && ch <= 'z') && !(ch >= 'A' && ch <= 'Z'))
                return false;
        }
        return true;
    }

    public static class LevCalculator {
        private int[][] lev;

        public LevCalculator() {
        }

        public LevCalculator(int length1, int length2) {
            allocate(length1, length2);
        }

        private void allocate(int length1, int length2) {
            length1 += 1;
            length2 += 1;
            lev = new int[length1][length2];
            for (int i = 1; i < length1; i++) {
                lev[i][0] = i;
            }
            int[] cache = lev[0];
            for (int i = 0; i < length2; i++) {
                cache[i] = i;
            }
        }

        public int getLength1() {
            return lev.length;
        }

        public int getLength2() {
            return lev[0].length;
        }

        private int min(int a, int b, int c) {
            return Math.min(a, Math.min(b, c));
        }

        public int calc(CharSequence a, CharSequence b) {
            if (lev == null || a.length() >= lev.length || b.length() >= lev[0].length) {
                allocate(a.length(), b.length());
            }

            int lengthA = a.length() + 1, lengthB = b.length() + 1;

            for (int i = 1; i < lengthA; i++) {
                for (int j = 1; j < lengthB; j++) {
                    lev[i][j] = min(
                            lev[i][j - 1] + 1, // insert
                            lev[i - 1][j] + 1, // del
                            a.charAt(i - 1) == b.charAt(j - 1) ? lev[i - 1][j - 1] : lev[i - 1][j - 1] + 1 // replace
                    );
                }
            }

            return lev[a.length()][b.length()];
        }
    }

    /**
     * Class for computing the longest common subsequence between strings.
     */
    public static final class LongestCommonSubsequence {
        // We reuse dynamic programming storage array here to reduce allocations.
        private final int[][] f;
        private final int maxLengthA;
        private final int maxLengthB;

        public LongestCommonSubsequence(int maxLengthA, int maxLengthB) {
            this.maxLengthA = maxLengthA;
            this.maxLengthB = maxLengthB;
            f = new int[maxLengthA + 1][];
            for (int i = 0; i <= maxLengthA; i++) {
                f[i] = new int[maxLengthB + 1];
            }
        }

        public int calc(CharSequence a, CharSequence b) {
            if (a.length() > maxLengthA || b.length() > maxLengthB) {
                throw new IllegalArgumentException("Too large length");
            }
            for (int i = 1; i <= a.length(); i++) {
                for (int j = 1; j <= b.length(); j++) {
                    if (a.charAt(i - 1) == b.charAt(j - 1)) {
                        f[i][j] = 1 + f[i - 1][j - 1];
                    } else {
                        f[i][j] = Math.max(f[i - 1][j], f[i][j - 1]);
                    }
                }
            }
            return f[a.length()][b.length()];
        }
    }
}
