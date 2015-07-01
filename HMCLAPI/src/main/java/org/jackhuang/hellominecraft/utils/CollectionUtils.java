/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.utils;

import org.jackhuang.hellominecraft.utils.functions.Consumer;
import org.jackhuang.hellominecraft.utils.functions.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 *
 * @author hyh
 */
public final class CollectionUtils {
    public static <T> void forEach(Collection<T> coll, Consumer<T> p) {
        for(T t : coll) p.accept(t);
    }
    
    public static <T> Collection<T> sortOut(Collection<T> coll, Predicate<T> p) {
        ArrayList<T> newColl = new ArrayList<>();
        forEach(coll, t -> { if(p.apply(t)) newColl.add(t); });
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
}
