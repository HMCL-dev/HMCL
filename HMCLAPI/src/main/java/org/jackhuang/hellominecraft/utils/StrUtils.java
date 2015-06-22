/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import java.awt.Dimension;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

/**
 *
 * @author huang
 */
public final class StrUtils {

    public static String makeCommand(List<String> cmd) {
        StringBuilder cmdbuf = new StringBuilder(120);
        for (int i = 0; i < cmd.size(); i++) {
            if (i > 0) {
                cmdbuf.append(' ');
            }
            String s = cmd.get(i);
            if (s.indexOf(' ') >= 0 || s.indexOf('\t') >= 0) {
                if (s.charAt(0) != '"') {
                    cmdbuf.append('"');
                    cmdbuf.append(s);
                    if (s.endsWith("\\")) {
                        cmdbuf.append("\\");
                    }
                    cmdbuf.append('"');
                } else if (s.endsWith("\"")) {
                    /* The argument has already been quoted. */
                    cmdbuf.append(s);
                } else {
                    /* Unmatched quote for the argument. */
                    throw new IllegalArgumentException();
                }
            } else {
                cmdbuf.append(s);
            }
        }
        String str = cmdbuf.toString();

        return str;
    }

    public static boolean startsWith(String base, String match) {
        return base != null && base.startsWith(match);
    }

    public static boolean startsWithOne(String[] a, String match) {
        if (a == null) {
            return false;
        }
        for (String b : a) {
            if (startsWith(match, b)) {
                return true;
            }
        }
        return false;
    }

    public static boolean equalsOne(String base, String... a) {
        for (String s : a) {
            if (base.equals(s)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsOne(List<String> base, List<String> match) {
        for (String a : base) {
            for (String b : match) {
                if (a.toLowerCase().contains(b.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int getCharShowTime(String s, char c) {
        int res = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                res++;
            }
        }
        return res;
    }

    public static String formatVersion(String ver) {
        if (isBlank(ver)) {
            return null;
        } else {
            for(char ch : ver.toCharArray()) {
                if((ch < '0' || ch > '9') && ch != '.') return null;
            }
        }
        int i = getCharShowTime(ver, '.');
        if (i == 1) {
            return ver + ".0";
        } else {
            return ver;
        }
    }

    public static String parseParams(String addBefore, Collection paramArrayOfObject, String paramString) {
        return parseParams(addBefore, paramArrayOfObject.toArray(), paramString);
    }

    public static String parseParams(String addBefore, Object[] paramArrayOfObject, String paramString) {
        if (paramArrayOfObject == null) {
            return "";
        }
        StringBuilder localStringBuffer = new StringBuilder();
        for (int i = 0; i < paramArrayOfObject.length; i++) {
            Object localObject = paramArrayOfObject[i];
            if (i > 0) {
                localStringBuffer.append(addBefore).append(paramString);
            }
            if (localObject == null) {
                localStringBuffer.append("null");
            } else if (localObject.getClass().isArray()) {
                localStringBuffer.append("[");

                if ((localObject instanceof Object[])) {
                    Object[] arrayOfObject = (Object[]) localObject;
                    localStringBuffer.append(parseParams(addBefore, arrayOfObject, paramString));
                } else {
                    for (int j = 0; j < Array.getLength(localObject); j++) {
                        if (j > 0) {
                            localStringBuffer.append(paramString);
                        }
                        localStringBuffer.append(addBefore).append(Array.get(localObject, j));
                    }
                }
                localStringBuffer.append("]");
            } else {
                localStringBuffer.append(addBefore).append(paramArrayOfObject[i]);
            }
        }
        return localStringBuffer.toString();
    }

    public static boolean isEquals(String base, String to) {
        if (base == null) {
            return (to == null);
        } else {
            return base.equals(to);
        }
    }

    public static Dimension parseDimension(String str) {
        String[] tokenized = tokenize(str, "x,");
        if (tokenized.length != 2) {
            return null;
        }
        int i = MathUtils.parseInt(tokenized[0], -1);
        int j = MathUtils.parseInt(tokenized[1], -1);
        if ((i < 0) || (j < 0)) {
            return null;
        }
        return new Dimension(i, j);
    }

    public static String[] tokenize(String paramString1) {
        return tokenize(paramString1, " \t\n\r\f");
    }

    public static String[] tokenize(String paramString1, String paramString2) {
        ArrayList localArrayList = new ArrayList();
        StringTokenizer tokenizer = new StringTokenizer(paramString1, paramString2);
        while (tokenizer.hasMoreTokens()) {
            paramString2 = tokenizer.nextToken();
            localArrayList.add(paramString2);
        }

        return (String[]) localArrayList.toArray(new String[localArrayList.size()]);
    }

    public static String trimExtension(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int i = filename.lastIndexOf('.');
            if ((i > -1) && (i < (filename.length()))) {
                return filename.substring(0, i);
            }
        }
        return filename;
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
