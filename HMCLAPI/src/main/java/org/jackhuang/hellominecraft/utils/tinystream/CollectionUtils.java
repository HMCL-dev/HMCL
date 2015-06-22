/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils.tinystream;

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
