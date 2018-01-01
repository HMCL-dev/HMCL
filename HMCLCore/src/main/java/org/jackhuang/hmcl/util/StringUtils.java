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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

/**
 *
 * @author huangyuhui
 */
public final class StringUtils {

    private StringUtils() {
    }

    public static String makeCommand(List<String> cmd) {
        StringBuilder cmdbuf = new StringBuilder(120);
        for (int i = 0; i < cmd.size(); i++) {
            if (i > 0)
                cmdbuf.append(' ');
            String s = cmd.get(i);
            if (s.indexOf(' ') >= 0 || s.indexOf('\t') >= 0)
                if (s.charAt(0) != '"') {
                    cmdbuf.append('"');
                    cmdbuf.append(s);
                    if (s.endsWith("\\"))
                        cmdbuf.append("\\");
                    cmdbuf.append('"');
                } else if (s.endsWith("\""))
                    /*
                     * The argument has already been quoted.
                     */
                    cmdbuf.append(s);
                else
                    /*
                     * Unmatched quote for the argument.
                     */
                    throw new IllegalArgumentException();
            else
                cmdbuf.append(s);
        }
        String str = cmdbuf.toString();

        return str;
    }

    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
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

    public static String removeSurrounding(String str, String delimiter) {
        return removeSurrounding(str, delimiter, delimiter);
    }

    public static String removeSurrounding(String str, String prefix, String suffix) {
        if ((str.length() >= prefix.length() + suffix.length()) && str.startsWith(prefix) && str.endsWith(suffix))
            return str.substring(prefix.length(), str.length() - suffix.length());
        else
            return str;
    }

    public static String removePrefix(String str, String prefix) {
        if (str.startsWith(prefix))
            return str.substring(prefix.length(), str.length());
        else
            return str;
    }

    public static String removeSuffix(String str, String suffix) {
        if (str.endsWith(suffix))
            return str.substring(0, str.length() - suffix.length());
        else
            return str;
    }

    public static boolean containsOne(Collection<String> patterns, String... targets) {
        for (String pattern : patterns)
            for (String target : targets)
                if (pattern.toLowerCase().contains(target.toLowerCase()))
                    return true;
        return false;
    }

    public static List<String> tokenize(String str) {
        if (str == null)
            return new LinkedList<>();
        else
            return tokenize(str, " \t\n\r\f");
    }

    public static List<String> tokenize(String str, String delim) {
        LinkedList<String> result = new LinkedList<>();
        StringTokenizer tokenizer = new StringTokenizer(str, delim);
        while (tokenizer.hasMoreTokens()) {
            delim = tokenizer.nextToken();
            result.add(delim);
        }

        return result;
    }

    public static Integer parseInt(String str) {
        if (str == null)
            return null;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
