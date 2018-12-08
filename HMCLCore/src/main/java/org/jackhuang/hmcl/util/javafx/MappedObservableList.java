/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util.javafx;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;

import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toCollection;
import static javafx.collections.FXCollections.unmodifiableObservableList;

/**
 * @author yushijinhun
 */
public final class MappedObservableList {
    private MappedObservableList() {
    }

    private static class MappedObservableListUpdater<T, U> implements ListChangeListener<T> {
        private ObservableList<T> origin;
        private ObservableList<U> target;
        private Function<T, U> mapper;

        // If we directly synchronize changes to target, each operation on target will cause a event to be fired.
        // So we first write changes to buffer. After all the changes are processed, we use target.setAll to synchronize the changes.
        private List<U> buffer;

        MappedObservableListUpdater(ObservableList<T> origin, ObservableList<U> target, Function<T, U> mapper) {
            this.origin = origin;
            this.target = target;
            this.mapper = mapper;
            this.buffer = new ArrayList<>(target);
        }

        @Override
        public void onChanged(Change<? extends T> change) {
            // cache removed elements to reduce calls to mapper
            Map<T, LinkedList<U>> cache = new IdentityHashMap<>();

            while (change.next()) {
                int from = change.getFrom();
                int to = change.getTo();

                if (change.wasPermutated()) {
                    @SuppressWarnings("unchecked")
                    U[] temp = (U[]) new Object[to - from];
                    for (int i = 0; i < temp.length; i++) {
                        temp[i] = buffer.get(from + i);
                    }

                    for (int idx = from; idx < to; idx++) {
                        buffer.set(change.getPermutation(idx), temp[idx - from]);
                    }
                } else {
                    if (change.wasRemoved()) {
                        List<? extends T> originRemoved = change.getRemoved();
                        List<U> targetRemoved = buffer.subList(from, from + originRemoved.size());
                        for (int i = 0; i < targetRemoved.size(); i++) {
                            pushCache(cache, originRemoved.get(i), targetRemoved.get(i));
                        }
                        targetRemoved.clear();
                    }
                    if (change.wasAdded()) {
                        @SuppressWarnings("unchecked")
                        U[] toAdd = (U[]) new Object[to - from];
                        for (int i = 0; i < toAdd.length; i++) {
                            toAdd[i] = map(cache, origin.get(from + i));
                        }
                        buffer.addAll(from, Arrays.asList(toAdd));
                    }
                }
            }
            target.setAll(buffer);
        }

        private void pushCache(Map<T, LinkedList<U>> cache, T key, U value) {
            cache.computeIfAbsent(key, any -> new LinkedList<>())
                    .push(value);
        }

        private U map(Map<T, LinkedList<U>> cache, T key) {
            LinkedList<U> stack = cache.get(key);
            if (stack != null && !stack.isEmpty()) {
                return stack.pop();
            }
            return mapper.apply(key);
        }
    }

    /**
     * This methods creates a mapping of {@code origin}, using {@code mapper} as the converter.
     *
     * If an item is added to {@code origin}, {@code mapper} will be invoked to create a corresponding item, which will also be added to the returned {@code ObservableList}.
     * If an item is removed from {@code origin}, the corresponding item in the returned {@code ObservableList} will also be removed.
     * If {@code origin} is permutated, the returned {@code ObservableList} will also be permutated in the same way.
     *
     * The returned {@code ObservableList} is unmodifiable.
     */
    public static <T, U> ObservableList<U> create(ObservableList<T> origin, Function<T, U> mapper) {
        // create a already-synchronized target ObservableList<U>
        ObservableList<U> target = origin.stream()
                .map(mapper)
                .collect(toCollection(FXCollections::observableArrayList));

        // then synchronize further changes to target
        ListChangeListener<T> listener = new MappedObservableListUpdater<>(origin, target, mapper);

        // let target hold a reference to listener to prevent listener being garbage-collected before target is garbage-collected
        target.addListener(new ReferenceHolder(listener));

        // let origin hold a weak reference to listener, so that target can be garbage-collected when it's no longer used
        origin.addListener(new WeakListChangeListener<>(listener));

        // ref graph:
        // target ------> listener <-weak- origin
        //        <------          ------>

        return unmodifiableObservableList(target);
    }
}
