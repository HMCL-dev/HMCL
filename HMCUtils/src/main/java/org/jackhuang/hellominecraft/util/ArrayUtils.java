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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public final class ArrayUtils {

    private ArrayUtils() {
    }

    public static <T> boolean isEmpty(T[] array) {
        return array == null || array.length <= 0;
    }

    public static <T> boolean isNotEmpty(T[] array) {
        return !isEmpty(array);
    }

    public static <T> boolean contains(T[] array, T objectToFind) {
        return indexOf(array, objectToFind) != -1;
    }

    public static <T> int indexOf(T[] array, T valueToFind) {
        return indexOf(array, valueToFind, 0);
    }

    public static <T> int indexOf(T[] array, T valueToFind, int startIndex) {
        if (array == null)
            return -1;
        if (startIndex < 0)
            startIndex = 0;
        for (int i = startIndex; i < array.length; i++)
            if (valueToFind.equals(array[i]))
                return i;
        return -1;
    }

    public static <T> int lastIndexOf(T[] array, T valueToFind, int startIndex) {
        if (array == null)
            return -1;
        if (startIndex < 0)
            return -1;
        if (startIndex >= array.length)
            startIndex = array.length - 1;
        for (int i = startIndex; i >= 0; i--)
            if (valueToFind.equals(array[i]))
                return i;
        return -1;
    }

    public static <T> ArrayList<T> merge(List<T> a, List<T> b) {
        ArrayList<T> al = new ArrayList<>(a.size() + b.size());
        al.addAll(a);
        al.addAll(b);
        return al;
    }

    public static <T> List<T> tryGetMapWithList(Map<String, List<T>> map, String key) {
        List<T> l = (List<T>) map.get(key);
        if (l == null)
            map.put(key, l = new ArrayList<>());
        return l;
    }
    
    public static <T> int matchArray(byte[] a, byte[] b) {
        for (int i = 0; i < a.length - b.length; i++) {
            int j = 1;
            for (int k = 0; k < b.length; k++) {
                if (b[k] == a[(i + k)])
                    continue;
                j = 0;
                break;
            }
            if (j != 0)
                return i;
        }
        return -1;
    }
    
    public static <T> boolean hasDuplicateElements(T[] t) {
        return new HashSet<>(Arrays.asList(t)).size() < t.length;
    }
}
