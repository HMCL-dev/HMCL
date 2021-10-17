/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class AggregatedObservableList<T> {

    protected final List<ObservableList<T>> lists = new ArrayList<>();
    final private List<Integer> sizes = new ArrayList<>();
    final private List<InternalListModificationListener> listeners = new ArrayList<>();
    final protected ObservableList<T> aggregatedList = FXCollections.observableArrayList();

    public AggregatedObservableList() {

    }

    /**
     * The Aggregated Observable List. This list is unmodifiable, because sorting this list would mess up the entire bookkeeping we do here.
     *
     * @return an unmodifiable view of the aggregatedList
     */
    public ObservableList<T> getAggregatedList() {
        return aggregatedList;
    }

    public void appendList(@NotNull ObservableList<T> list) {
        assert !lists.contains(list) : "List is already contained: " + list;
        lists.add(list);
        final InternalListModificationListener listener = new InternalListModificationListener(list);
        list.addListener(listener);
        //System.out.println("list = " + list + " puttingInMap=" + list.hashCode());
        sizes.add(list.size());
        aggregatedList.addAll(list);
        listeners.add(listener);
        assert lists.size() == sizes.size() && lists.size() == listeners.size() :
                "lists.size=" + lists.size() + " not equal to sizes.size=" + sizes.size() + " or not equal to listeners.size=" + listeners.size();
    }

    public void prependList(@NotNull ObservableList<T> list) {
        assert !lists.contains(list) : "List is already contained: " + list;
        lists.add(0, list);
        final InternalListModificationListener listener = new InternalListModificationListener(list);
        list.addListener(listener);
        //System.out.println("list = " + list + " puttingInMap=" + list.hashCode());
        sizes.add(0, list.size());
        aggregatedList.addAll(0, list);
        listeners.add(0, listener);
        assert lists.size() == sizes.size() && lists.size() == listeners.size() :
                "lists.size=" + lists.size() + " not equal to sizes.size=" + sizes.size() + " or not equal to listeners.size=" + listeners.size();
    }

    public void removeList(@NotNull ObservableList<T> list) {
        assert lists.size() == sizes.size() && lists.size() == listeners.size() :
                "lists.size=" + lists.size() + " not equal to sizes.size=" + sizes.size() + " or not equal to listeners.size=" + listeners.size();
        final int index = lists.indexOf(list);
        if (index < 0) {
            throw new IllegalArgumentException("Cannot remove a list that is not contained: " + list + " lists=" + lists);
        }
        final int startIndex = getStartIndex(list);
        final int endIndex = getEndIndex(list, startIndex);
        // we want to find the start index of this list inside the aggregated List. End index will be start + size - 1.
        lists.remove(list);
        sizes.remove(index);
        final InternalListModificationListener listener = listeners.remove(index);
        list.removeListener(listener);
        aggregatedList.remove(startIndex, endIndex + 1); // end + 1 because end is exclusive
        assert lists.size() == sizes.size() && lists.size() == listeners.size() :
                "lists.size=" + lists.size() + " not equal to sizes.size=" + sizes.size() + " or not equal to listeners.size=" + listeners.size();
    }

    /**
     * Get the start index of this list inside the aggregated List.
     * This is a private function. we can safely asume, that the list is in the map.
     *
     * @param list the list in question
     * @return the start index of this list in the aggregated List
     */
    private int getStartIndex(@NotNull ObservableList<T> list) {
        int startIndex = 0;
        //System.out.println("=== searching startIndex of " + list);
        assert lists.size() == sizes.size() : "lists.size=" + lists.size() + " not equal to sizes.size=" + sizes.size();
        final int listIndex = lists.indexOf(list);
        for (int i = 0; i < listIndex; i++) {
            final Integer size = sizes.get(i);
            startIndex += size;
            //System.out.println(" startIndex = " + startIndex + " added=" + size);
        }
        //System.out.println("startIndex = " + startIndex);
        return startIndex;
    }

    /**
     * Get the end index of this list inside the aggregated List.
     * This is a private function. we can safely asume, that the list is in the map.
     *
     * @param list       the list in question
     * @param startIndex the start of the list (retrieve with {@link #getStartIndex(ObservableList)}
     * @return the end index of this list in the aggregated List
     */
    private int getEndIndex(@NotNull ObservableList<T> list, int startIndex) {
        assert lists.size() == sizes.size() : "lists.size=" + lists.size() + " not equal to sizes.size=" + sizes.size();
        final int index = lists.indexOf(list);
        return startIndex + sizes.get(index) - 1;
    }

    private class InternalListModificationListener implements ListChangeListener<T> {

        @NotNull
        private final ObservableList<T> list;

        public InternalListModificationListener(@NotNull ObservableList<T> list) {
            this.list = list;
        }

        /**
         * Called after a change has been made to an ObservableList.
         *
         * @param change an object representing the change that was done
         * @see Change
         */
        @Override
        public void onChanged(Change<? extends T> change) {
            final ObservableList<? extends T> changedList = change.getList();
            final int startIndex = getStartIndex(list);
            final int index = lists.indexOf(list);
            final int newSize = changedList.size();
            //System.out.println("onChanged for list=" + list + " aggregate=" + aggregatedList);
            while (change.next()) {
                final int from = change.getFrom();
                final int to = change.getTo();
                //System.out.println(" startIndex=" + startIndex + " from=" + from + " to=" + to);
                if (change.wasPermutated()) {
                    final ArrayList<T> copy = new ArrayList<>(aggregatedList.subList(startIndex + from, startIndex + to));
                    //System.out.println("  permutating sublist=" + copy);
                    for (int oldIndex = from; oldIndex < to; oldIndex++) {
                        int newIndex = change.getPermutation(oldIndex);
                        copy.set(newIndex - from, aggregatedList.get(startIndex + oldIndex));
                    }
                    //System.out.println("  permutating done sublist=" + copy);
                    aggregatedList.subList(startIndex + from, startIndex + to).clear();
                    aggregatedList.addAll(startIndex + from, copy);
                } else if (change.wasUpdated()) {
                    // do nothing
                } else {
                    if (change.wasRemoved()) {
                        List<? extends T> removed = change.getRemoved();
                        //System.out.println("  removed= " + removed);
                        // IMPORTANT! FROM == TO when removing items.
                        aggregatedList.remove(startIndex + from, startIndex + from + removed.size());
                    }
                    if (change.wasAdded()) {
                        List<? extends T> added = change.getAddedSubList();
                        //System.out.println("  added= " + added);
                        //add those elements to your data
                        aggregatedList.addAll(startIndex + from, added);
                    }
                }
            }
            // update the size of the list in the map
            //System.out.println("list = " + list + " puttingInMap=" + list.hashCode());
            sizes.set(index, newSize);
            //System.out.println("listSizesMap = " + sizes);
        }

    }

    public String dump() {
        return dump(x -> x);
    }

    public String dump(Function<T, Object> function) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        aggregatedList.forEach(el -> sb.append(function.apply(el)).append(","));
        final int length = sb.length();
        sb.replace(length - 1, length, "");
        sb.append("]");
        return sb.toString();
    }
}
