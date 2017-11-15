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
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.api.func.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jackhuang.hmcl.api.func.Function;

/**
 *
 * @author huangyuhui
 */
public final class CollectionUtils {

    private CollectionUtils() {
    }

    public static <T> List<T> filter(Collection<T> coll, Predicate<T> p) {
        ArrayList<T> newColl = new ArrayList<>();
        for (T t : coll)
            if (p.apply(t))
                newColl.add(t);
        return newColl;
    }

    public static <U, V> List<V> map(Collection<U> coll, Function<U, V> p) {
        ArrayList<V> newColl = new ArrayList<>(coll.size());
        for (U t : coll)
            newColl.add(p.apply(t));
        return newColl;
    }

    public static <U, V> List<V> flatMap(Collection<U> coll, Function<U, Collection<V>> p) {
        ArrayList<V> newColl = new ArrayList<>(coll.size());
        for (U t : coll)
            newColl.addAll(p.apply(t));
        return newColl;
    }

    public static <T> boolean removeIf(Collection<T> coll, Predicate<T> p) {
        boolean removed = false;
        final Iterator<T> each = coll.iterator();
        while (each.hasNext())
            if (p.apply(each.next())) {
                each.remove();
                removed = true;
            }
        return removed;
    }
    
    public static <T extends Cloneable> ArrayList<T> deepCopy(List<T> original, Function<T, T> clone) {
        if (original == null)
            return null;
        ArrayList<T> ret = new ArrayList<>(original.size());
        for (T x : original)
            ret.add(clone.apply(x));
        return ret;
    }
    
    public static <T> ArrayList<T> copy(List<T> original) {
        if (original == null)
            return null;
        ArrayList<T> ret = new ArrayList<>(original.size());
        for (T x : original)
            ret.add(x);
        return ret;
    }
    
    public static <K, V extends Cloneable> Map<K, V> deepCopy(Map<K, V> original, Function<V, V> clone) {
        if (original == null)
            return null;
        HashMap<K, V> ret = new HashMap<>(original.size());
        for (HashMap.Entry<K, V> x : original.entrySet())
            ret.put(x.getKey(), clone.apply(x.getValue()));
        return ret;
    }
    
    public static <K, V> Map<K, V> copy(Map<K, V> original) {
        if (original == null)
            return null;
        HashMap<K, V> ret = new HashMap<>(original.size());
        for (HashMap.Entry<K, V> x : original.entrySet())
            ret.put(x.getKey(), x.getValue());
        return ret;
    }
}
