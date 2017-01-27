/*
 * Hello Minecraft!.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;
import org.jackhuang.hellominecraft.util.func.Function;
import org.jackhuang.hellominecraft.util.func.Predicate;

/**
 *
 * @author huang
 */
public final class StrUtils {
    
    private StrUtils() {
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

    public static boolean startsWith(String base, String match) {
        return base != null && base.startsWith(match);
    }

    public static boolean startsWithOne(Collection<String> a, String match) {
        if (a == null)
            return false;
        for (String b : a)
            if (startsWith(match, b))
                return true;
        return false;
    }

    public static boolean equalsOne(String base, String... a) {
        for (String s : a)
            if (base.equals(s))
                return true;
        return false;
    }

    public static boolean containsOne(List<String> base, List<String> match, Predicate<String> pred) {
        for (String a : base)
            for (String b : match)
                if (pred.apply(a) && a.toLowerCase().contains(b.toLowerCase()))
                    return true;
        return false;
    }

    public static int getCharShowTime(String s, char c) {
        int res = 0;
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) == c)
                res++;
        return res;
    }

    public static String formatVersion(String ver) {
        if (isBlank(ver))
            return null;
        else
            for (char ch : ver.toCharArray())
                if ((ch < '0' || ch > '9') && ch != '.')
                    return null;
        int i = getCharShowTime(ver, '.');
        if (i == 1)
            return ver + ".0";
        else
            return ver;
    }

    public static String parseParams(String addBefore, Collection<?> objects, String addAfter) {
        return parseParams(addBefore, objects.toArray(), addAfter);
    }

    public static String parseParams(String addBefore, Object[] objects, String addAfter) {
        return parseParams(t -> addBefore, objects, t -> addAfter);
    }

    public static String parseParams(Function<Object, String> beforeFunc, Object[] params, Function<Object, String> afterFunc) {
        if (params == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            String addBefore = beforeFunc.apply(param), addAfter = afterFunc.apply(param);
            if (i > 0)
                sb.append(addAfter).append(addBefore);
            if (param == null)
                sb.append("null");
            else if (param.getClass().isArray()) {
                sb.append("[");
                if ((param instanceof Object[])) {
                    Object[] objs = (Object[]) param;
                    sb.append(parseParams(beforeFunc, objs, afterFunc));
                } else
                    for (int j = 0; j < Array.getLength(param); j++) {
                        if (j > 0)
                            sb.append(addAfter);
                        sb.append(addBefore).append(Array.get(param, j));
                    }
                sb.append("]");
            } else
                sb.append(addBefore).append(params[i]);
        }
        return sb.toString();
    }

    public static String[] tokenize(String paramString1) {
        return tokenize(paramString1, " \t\n\r\f");
    }

    public static String[] tokenize(String str, String delim) {
        ArrayList<String> al = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(str, delim);
        while (tokenizer.hasMoreTokens()) {
            delim = tokenizer.nextToken();
            al.add(delim);
        }

        return al.toArray(new String[al.size()]);
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().length() <= 0;
    }

    public static boolean isNotBlank(String s) {
        return !isBlank(s);
    }

    public static String getStackTrace(Throwable t) {
        StringWriter trace = new StringWriter();
        PrintWriter writer = new PrintWriter(trace);
        t.printStackTrace(writer);
        return trace.toString();
    }
}
