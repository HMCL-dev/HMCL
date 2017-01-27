/*  The contents of this file are subject to the terms of the Common Development
and Distribution License (the License). You may not use this file except in
compliance with the License.
    You can obtain a copy of the License at http://www.netbeans.org/cddl.html
or http://www.netbeans.org/cddl.txt.
    When distributing Covered Code, include this CDDL Header Notice in each file
and include the License file at http://www.netbeans.org/cddl.txt.
If applicable, add the following below the CDDL Header, with the fields
enclosed by brackets [] replaced by your own identifying information:
"Portions Copyrighted [year] [name of copyright owner]" */
 /*
 * MergeMap.java
 *
 * Created on February 22, 2005, 4:06 PM
 */
package org.jackhuang.hellominecraft.util.ui.wizard.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;

/**
 * A map which proxies a collection of sub-maps each of which has a
 * unique id. Submaps can be added or removed en banc. Values from
 * removed maps are retained; if push ("someKnownId") happens, the
 * values previously added to the map while that ID was active reappear.
 * <p>
 * This allows us to implement backward/forward semantics for wizards,
 * in which each pane (identified with a unique ID) can add its own
 * settings to the settings map, but if the user presses the Back
 * button, the settings from the formerly active pane can disappear -
 * but if the user moves forward again, they are not lost.
 * <p>
 * Calling remove("someKeyBelongingToAnEarlierId") will completely
 * remove that value; calling put ("someKeyBelongingToAnEarlierId", "newValue")
 * replaces the earler value permanently.
 * <p>
 * <b><i><font color="red">This class is NOT AN API CLASS. There is no
 * commitment that it will remain backward compatible or even exist in the
 * future. The API of this library is in the packages
 * <code>org.netbeans.api.wizard</code>
 * and <code>org.netbeans.spi.wizard</code></font></i></b>.
 *
 * @author Tim Boudreau
 */
public class MergeMap<K, V> implements Map<K, V> {

    private final Stack<String> order = new Stack<>();
    private final Map<String, Map<K, V>> id2map = new HashMap<>();

    /**
     * Creates a new instance of MergeMap
     */
    public MergeMap(String currID) {
        push(currID);
    }

    private static final String BASE = "__BASE";

    /**
     * Creates a MergeMap with a set of key/value pairs that are
     * always there (they came from a legacy wizard - used for bridging the
     * old NetBeans wizards API and this one - some bridged wizards will
     * have a first panel that gathered some settings using the old APIs
     * framework, and we need to inject them here.
     */
    public MergeMap(String currId, Map<K, V> everpresent) {
        order.push(BASE);
        id2map.put(BASE, everpresent);
        push(currId);
    }

    /**
     * Move to a different ID (meaning add a new named map to proxy which can be
     * calved off if necessary).
     */
    public Map<K, V> push(String id) {
        // assert !order.contains(id) : id + " already present";
        if (order.contains(id))
            throw new RuntimeException(id + " already present");
//        assert !order.contains(id) : id + " already present";
        if (!order.isEmpty() && id.equals(order.peek()))
            return (Map<K, V>) id2map.get(id);
        Map<K, V> result = (Map<K, V>) id2map.get(id);
        if (result == null) {
            result = new HashMap<>();
            id2map.put(id, result);
        }
        order.push(id);
        return result;
    }

    /**
     * Get the ID of the current sub-map being written into.
     */
    public String currID() {
        return (String) order.peek();
    }

    /**
     * Remove the current sub-map. Removes all of its settings from the
     * MergedMap, but if push() is called with the returned value, the
     * values associated with the ID being removed will be restored.
     */
    public String popAndCalve() {
        if (order.isEmpty())
            throw new NoSuchElementException("Cannot back out past first "
                                             + "entry");
        //Get the current map
        String result = (String) order.peek();
        Map<K, V> curr = (Map<K, V>) id2map.get(result);
        order.pop();

        //Though unlikely, it is possible that a later step in a wizard
        //overwrote a key/value pair from a previous step of the wizard.
        //We do not want to revert that write, so iterate all the keys
        //we're removing, and if any of them are in steps lower on the
        //stack, change those lower steps values to whatever was written
        //into the map we're calving off
        for (Iterator<String> i = orderIterator(); i.hasNext();) {
            Map<K, V> other = (Map<K, V>) id2map.get(i.next());
            for (K key : curr.keySet())
                if (other.containsKey(key))
                    other.put(key, curr.get(key));
        }
        return result;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsKey(Object obj) {
        for (Iterator<String> i = orderIterator(); i.hasNext();) {
            Map<K, V> curr = (Map<K, V>) id2map.get(i.next());
            if (curr.containsKey(obj))
                return true;
        }
        return false;
    }

    @Override
    public boolean containsValue(Object obj) {
        for (Iterator<String> i = orderIterator(); i.hasNext();) {
            Map<K, V> curr = (Map<K, V>) id2map.get(i.next());
            if (curr.containsValue(obj))
                return true;
        }
        return false;
    }

    @Override
    public java.util.Set<Entry<K, V>> entrySet() {
        HashSet<Entry<K, V>> result = new HashSet<>();
        for (Iterator<String> i = orderIterator(); i.hasNext();) {
            Map<K, V> curr = (Map<K, V>) id2map.get(i.next());
            result.addAll(curr.entrySet());
        }
        return result;
    }

    @Override
    public V get(Object obj) {
        for (Iterator<String> i = orderIterator(); i.hasNext();) {
            String id = (String) i.next();
            Map<K, V> curr = (Map<K, V>) id2map.get(id);
            V result = curr.get(obj);
            if (result != null)
                return result;
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Set<K> keySet() {
        HashSet<K> result = new HashSet<>();
        for (Iterator<String> i = orderIterator(); i.hasNext();) {
            Map<K, V> curr = (Map<K, V>) id2map.get(i.next());
            result.addAll(curr.keySet());
        }
        return result;
    }

    @Override
    public V put(K obj, V obj1) {
        Map<K, V> curr = (Map<K, V>) id2map.get(order.peek());
        return curr.put(obj, obj1);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        Map<K, V> curr = (Map<K, V>) id2map.get(order.peek());
        curr.putAll(map);
    }

    private V doRemove(Object obj) {
        Map<K, V> curr = (Map<K, V>) id2map.get(order.peek());
        V result = curr.remove(obj);
        if (result == null)
            for (Iterator<String> i = orderIterator(); i.hasNext();) {
                curr = (Map<K, V>) id2map.get(i.next());
                result = curr.remove(obj);
                if (result != null)
                    break;
            }
        return result;
    }

    @Override
    public V remove(Object obj) {
        //Ensure we remove any duplicates in upper arrays
        V result = get(obj);
        while (get(obj) != null)
            doRemove(obj);
        return result;
    }

    @Override
    public int size() {
        //using keySet() prunes duplicates
        return keySet().size();
    }

    @Override
    public Collection<V> values() {
        HashSet<V> result = new HashSet<>();
        Set<K> keys = keySet();
        for (Iterator<K> i = keys.iterator(); i.hasNext();)
            result.add(get(i.next()));
        return result;
    }

    private Iterator<String> orderIterator() {
        return new ReverseIterator(order);
    }

    private static final class ReverseIterator implements Iterator<String> {

        private int pos;
        private final List<String> l;

        public ReverseIterator(Stack<String> s) {
            pos = s.size() - 1;
            l = new ArrayList<>(s);
        }

        @Override
        public boolean hasNext() {
            return pos != -1;
        }

        @Override
        public String next() {
            if (pos < 0)
                throw new NoSuchElementException();
            String result = l.get(pos);
            pos--;
            return result;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

}
