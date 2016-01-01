/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraftlauncher.apis.utils;

import java.awt.Dimension;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 *
 * @author huang
 */
public class StrUtils {
    

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
}
