package org.jackhuang.hmcl.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Testing the AggregatedObservableList
 */
public class AggregatedObservableListTest {


    @Test
    public void testInteger() {
        final AggregatedObservableList<Integer> aggregatedWrapper = new AggregatedObservableList<>();
        final ObservableList<Integer> aggregatedList = aggregatedWrapper.getAggregatedList();

        final ObservableList<Integer> list1 = FXCollections.observableArrayList();
        final ObservableList<Integer> list2 = FXCollections.observableArrayList();
        final ObservableList<Integer> list3 = FXCollections.observableArrayList();

        list1.addAll(1, 2, 3, 4, 5);
        list2.addAll(10, 11, 12, 13, 14, 15);
        list3.addAll(100, 110, 120, 130, 140, 150);

        // adding list 1 to aggregate
        aggregatedWrapper.appendList(list1);
        assertEquals("[1,2,3,4,5]", aggregatedWrapper.dump());

        // removing elems from list1
        list1.remove(2, 4);
        assertEquals("[1,2,5]", aggregatedWrapper.dump());

        // adding second List
        aggregatedWrapper.appendList(list2);
        assertEquals("[1,2,5,10,11,12,13,14,15]", aggregatedWrapper.dump());

        // removing elems from second List
        list2.remove(1, 3);
        assertEquals("[1,2,5,10,13,14,15]", aggregatedWrapper.dump());

        // replacing element in first list
        list1.set(1, 3);
        assertEquals("[1,3,5,10,13,14,15]", aggregatedWrapper.dump());

        // adding third List
        aggregatedWrapper.appendList(list3);
        assertEquals("[1,3,5,10,13,14,15,100,110,120,130,140,150]", aggregatedWrapper.dump());

        // emptying second list
        list2.clear();
        assertEquals("[1,3,5,100,110,120,130,140,150]", aggregatedWrapper.dump());

        // adding new elements to second list
        list2.addAll(203, 202, 201);
        assertEquals("[1,3,5,203,202,201,100,110,120,130,140,150]", aggregatedWrapper.dump());

        // sorting list2. this results in permutation
        list2.sort(Integer::compareTo);
        assertEquals("[1,3,5,201,202,203,100,110,120,130,140,150]", aggregatedWrapper.dump());

        // removing list2 completely
        aggregatedWrapper.removeList(list2);
        assertEquals("[1,3,5,100,110,120,130,140,150]", aggregatedWrapper.dump());

        // updating one integer value in list 3
        list3.set(0, 1);
        assertEquals("[1,3,5,1,110,120,130,140,150]", aggregatedWrapper.dump());

        // prepending list 2 again
        aggregatedWrapper.prependList(list2);
        assertEquals("[201,202,203,1,3,5,1,110,120,130,140,150]", aggregatedWrapper.dump());

    }

    @Test
    public void testSetAll() {
        final AggregatedObservableList<Integer> aggregatedWrapper = new AggregatedObservableList<>();
        final ObservableList<Integer> aggregatedList = aggregatedWrapper.getAggregatedList();

        final ObservableList<Integer> a = FXCollections.singletonObservableList(1);
        final ObservableList<Integer> b = FXCollections.observableArrayList(2, 3, 4);

        aggregatedWrapper.appendList(a);
        aggregatedWrapper.appendList(b);

        assertEquals("[1,2,3,4]", aggregatedWrapper.dump());

        b.setAll(7, 8, 9);
        assertEquals("[1,7,8,9]", aggregatedWrapper.dump());
    }
}