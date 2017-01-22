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

import org.jackhuang.hellominecraft.util.func.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 *
 * @author huangyuhui
 */
public final class CollectionUtils {

    private CollectionUtils() {
    }

    public static <T> ArrayList<T> filter(Collection<T> coll, Predicate<T> p) {
        ArrayList<T> newColl = new ArrayList<>();
        for (T t : coll)
            if (p.apply(t))
                newColl.add(t);
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
