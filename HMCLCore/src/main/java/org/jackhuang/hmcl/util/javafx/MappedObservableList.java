/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.TransformationList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// @author Glavo
public final class MappedObservableList<E, F> extends TransformationList<E, F> {

    /// This method creates a mapping of `source`, using `mapper` as the converter.
    ///
    /// If an item is added to `source`, `mapper` will be invoked to create a corresponding item, which will also be added to the returned `ObservableList`.
    /// If an item is removed from `source`, the corresponding item in the returned `ObservableList` will also be removed.
    /// If `source` is permutated, the returned `ObservableList` will also be permutated in the same way.
    ///
    /// The returned `ObservableList` is unmodifiable.
    public static <T, U> ObservableList<U> create(ObservableList<T> source, Function<T, U> mapper) {
        return new MappedObservableList<>(source, mapper);
    }

    private final Function<? super F, ? extends E> mapper;
    private final List<E> elements;

    public MappedObservableList(@NotNull ObservableList<? extends F> source, @NotNull Function<? super F, ? extends E> mapper) {
        super(source);
        this.mapper = mapper;
        this.elements = new ArrayList<>(source.size());
        for (F f : source) {
            elements.add(mapper.apply(f));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void sourceChanged(ListChangeListener.Change<? extends F> change) {
        beginChange();
        while (change.next()) {
            int from = change.getFrom();
            int to = change.getTo();

            if (change.wasPermutated()) {
                Object[] temp = new Object[to - from];
                int[] permutations = new int[to - from];

                for (int i = 0; i < temp.length; i++) {
                    temp[i] = elements.get(from + i);
                }

                for (int i = from; i < to; i++) {
                    int n = i - from;
                    int permutation = change.getPermutation(i);
                    permutations[n] = permutation;
                    elements.set(permutation, (E) temp[n]);
                }

                nextPermutation(from, to, permutations);
            } else if (change.wasUpdated()) {
                for (int i = from; i < to; i++) {
                    elements.set(i, mapper.apply(getSource().get(i)));
                    nextUpdate(i);
                }
            } else {
                List<E> removed = List.of();
                if (change.wasRemoved()) {
                    List<E> subList = elements.subList(from, from + change.getRemovedSize());
                    removed = new ArrayList<>(subList);
                    subList.clear();
                }

                if (change.wasAdded()) {
                    Object[] temp = new Object[to - from];
                    List<? extends F> addedSubList = change.getAddedSubList();
                    for (int i = 0; i < addedSubList.size(); i++) {
                        temp[i] = mapper.apply(addedSubList.get(i));
                    }
                    elements.addAll(from, (List<E>) Arrays.asList(temp));
                }

                if (change.wasRemoved() && change.wasAdded()) {
                    nextReplace(from, to, removed);
                } else if (change.wasRemoved()) {
                    nextRemove(from, removed);
                } else if (change.wasAdded()) {
                    nextAdd(from, to);
                }
            }
        }
        endChange();
    }

    @Override
    public E get(int index) {
        return elements.get(index);
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public int getSourceIndex(int index) {
        Objects.checkIndex(index, this.size());
        return index;
    }

    @Override
    public int getViewIndex(int index) {
        Objects.checkIndex(index, this.size());
        return index;
    }

}
