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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableListBase;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/// @author Glavo
public class MappedObservableListTest {

    @Test
    public void testInitialMapping() {
        ObservableList<Integer> source = FXCollections.observableArrayList(1, 2, 3, 4, 5);
        ObservableList<String> mapped = MappedObservableList.create(source, i -> "Item-" + i);

        assertEquals(5, mapped.size());
        assertEquals("Item-1", mapped.get(0));
        assertEquals("Item-2", mapped.get(1));
        assertEquals("Item-3", mapped.get(2));
        assertEquals("Item-4", mapped.get(3));
        assertEquals("Item-5", mapped.get(4));
    }

    @Test
    public void testAdd() {
        ObservableList<Integer> source = FXCollections.observableArrayList(1, 2, 3);
        ObservableList<String> mapped = MappedObservableList.create(source, i -> "Item-" + i);

        source.add(4);
        assertEquals(4, mapped.size());
        assertEquals("Item-4", mapped.get(3));

        source.add(1, 10);
        assertEquals(5, mapped.size());
        assertEquals("Item-10", mapped.get(1));
        assertEquals("Item-2", mapped.get(2));
    }

    @Test
    public void testRemove() {
        ObservableList<Integer> source = FXCollections.observableArrayList(1, 2, 3, 4, 5);
        ObservableList<String> mapped = MappedObservableList.create(source, i -> "Item-" + i);

        source.remove(2);
        assertEquals(4, mapped.size());
        assertEquals("Item-1", mapped.get(0));
        assertEquals("Item-2", mapped.get(1));
        assertEquals("Item-4", mapped.get(2));
        assertEquals("Item-5", mapped.get(3));

        source.remove(Integer.valueOf(1));
        assertEquals(3, mapped.size());
        assertEquals("Item-2", mapped.get(0));
        assertEquals("Item-4", mapped.get(1));
        assertEquals("Item-5", mapped.get(2));

        source.remove(1, 3);
        assertEquals(1, mapped.size());
        assertEquals("Item-2", mapped.get(0));
    }

    @Test
    public void testSet() {
        ObservableList<Integer> source = FXCollections.observableArrayList(1, 2, 3, 4, 5);
        ObservableList<String> mapped = MappedObservableList.create(source, i -> "Item-" + i);

        source.set(2, 10);
        assertEquals(5, mapped.size());

        // Verify the actual values
        assertEquals("Item-1", mapped.get(0));
        assertEquals("Item-2", mapped.get(1));
        assertEquals("Item-10", mapped.get(2));
        assertEquals("Item-4", mapped.get(3));
        assertEquals("Item-5", mapped.get(4));
    }

    @Test
    public void testSort() {
        ObservableList<Integer> source = FXCollections.observableArrayList(5, 3, 1, 4, 2);
        ObservableList<String> mapped = MappedObservableList.create(source, i -> "Item-" + i);

        FXCollections.sort(source);

        assertEquals(5, mapped.size());
        assertEquals("Item-1", mapped.get(0));
        assertEquals("Item-2", mapped.get(1));
        assertEquals("Item-3", mapped.get(2));
        assertEquals("Item-4", mapped.get(3));
        assertEquals("Item-5", mapped.get(4));
    }

    @Test
    public void testClear() {
        ObservableList<Integer> source = FXCollections.observableArrayList(1, 2, 3, 4, 5);
        ObservableList<String> mapped = MappedObservableList.create(source, i -> "Item-" + i);

        source.clear();

        assertEquals(0, mapped.size());
        assertTrue(mapped.isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> mapped.get(0));
    }

    @Test
    public void testAddAll() {
        ObservableList<Integer> source = FXCollections.observableArrayList(1, 2);
        ObservableList<String> mapped = MappedObservableList.create(source, i -> "Item-" + i);

        source.addAll(3, 4, 5);

        assertEquals(5, mapped.size());
        assertEquals("Item-3", mapped.get(2));
        assertEquals("Item-4", mapped.get(3));
        assertEquals("Item-5", mapped.get(4));
    }

    @Test
    public void testRemoveAll() {
        ObservableList<Integer> source = FXCollections.observableArrayList(1, 2, 3, 4, 5);
        ObservableList<String> mapped = MappedObservableList.create(source, i -> "Item-" + i);

        source.removeAll(2, 4);

        assertEquals(3, mapped.size());
        assertEquals("Item-1", mapped.get(0));
        assertEquals("Item-3", mapped.get(1));
        assertEquals("Item-5", mapped.get(2));
    }

    @Test
    public void testSetAll() {
        ObservableList<Integer> source = FXCollections.observableArrayList(1, 2, 3);
        ObservableList<String> mapped = MappedObservableList.create(source, i -> "Item-" + i);

        source.setAll(10, 20, 30, 40);

        assertEquals(4, mapped.size());
        assertEquals("Item-10", mapped.get(0));
        assertEquals("Item-20", mapped.get(1));
        assertEquals("Item-30", mapped.get(2));
        assertEquals("Item-40", mapped.get(3));
    }

    @Test
    public void testGetSourceIndex() {
        ObservableList<Integer> source = FXCollections.observableArrayList(1, 2, 3, 4, 5);
        MappedObservableList<String, Integer> mapped = new MappedObservableList<>(source, i -> "Item-" + i);

        assertEquals(0, mapped.getSourceIndex(0));
        assertEquals(2, mapped.getSourceIndex(2));
        assertEquals(4, mapped.getSourceIndex(4));

        assertThrows(IndexOutOfBoundsException.class, () -> mapped.getSourceIndex(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> mapped.getSourceIndex(5));
    }

    @Test
    public void testGetViewIndex() {
        ObservableList<Integer> source = FXCollections.observableArrayList(1, 2, 3, 4, 5);
        MappedObservableList<String, Integer> mapped = new MappedObservableList<>(source, i -> "Item-" + i);

        assertEquals(0, mapped.getViewIndex(0));
        assertEquals(2, mapped.getViewIndex(2));
        assertEquals(4, mapped.getViewIndex(4));

        assertThrows(IndexOutOfBoundsException.class, () -> mapped.getViewIndex(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> mapped.getViewIndex(5));
    }

    @Test
    public void testComplexOperations() {
        ObservableList<Integer> source = FXCollections.observableArrayList();
        ObservableList<String> mapped = MappedObservableList.create(source, i -> "Item-" + i);

        // Start with empty
        assertEquals(0, mapped.size());

        // Add some elements
        source.addAll(1, 2, 3, 4, 5);
        assertEquals(5, mapped.size());

        // Remove middle element
        source.remove(2);
        assertEquals(4, mapped.size());
        assertEquals("Item-4", mapped.get(2));

        // Sort
        FXCollections.sort(source, Collections.reverseOrder());
        assertEquals("Item-5", mapped.get(0));
        assertEquals("Item-4", mapped.get(1));
        assertEquals("Item-2", mapped.get(2));
        assertEquals("Item-1", mapped.get(3));

        // Add at specific position
        source.add(2, 3);
        assertEquals(5, mapped.size());
        assertEquals("Item-3", mapped.get(2));
    }

    /// Test for [javafx.collections.ListChangeListener.Change#wasUpdated()].
    @Test
    public void testUpdate() {
        class TestUpdateList<T> extends ObservableListBase<T> {
            private final List<T> backingList;

            @SafeVarargs
            public TestUpdateList(T... items) {
                this.backingList = Arrays.asList(items);
            }

            @Override
            public int size() {
                return backingList.size();
            }

            @Override
            public T get(int index) {
                return backingList.get(index);
            }

            public void updateItem(int beginIndex, int endIndex, Function<T, T> mapper) {
                Objects.checkFromToIndex(beginIndex, endIndex, size());
                beginChange();
                for (int i = beginIndex; i < endIndex; i++) {
                    backingList.set(i, mapper.apply(backingList.get(i)));
                    nextUpdate(i);
                }
                endChange();
            }
        }

        TestUpdateList<Integer> source = new TestUpdateList<>(1, 2, 3, 4, 5);
        ObservableList<String> mapped = MappedObservableList.create(source, i -> "Item-" + i);

        source.updateItem(2, 4, i -> i * 10);
        assertEquals(5, mapped.size());
        assertEquals("Item-1", mapped.get(0));
        assertEquals("Item-2", mapped.get(1));
        assertEquals("Item-30", mapped.get(2));
        assertEquals("Item-40", mapped.get(3));
        assertEquals("Item-5", mapped.get(4));
    }
}
