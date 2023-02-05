package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.function.ExceptionalRunnable;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Glavo
 */
public class CircularArrayListTest {
    private static void assertThrows(Class<? extends Throwable> type, ExceptionalRunnable<?> action) {
        try {
            action.run();
            throw new AssertionError();
        } catch (Throwable e) {
            if (!type.isInstance(e)) {
                if (e instanceof AssertionError)
                    throw (AssertionError) e;
                else
                    throw new AssertionError("Unexpected exception type thrown: " + e.getClass(), e);
            }
        }
    }

    private static void assertEmpty(CircularArrayList<?> list) {
        assertEquals(0, list.size());
        assertTrue(list.isEmpty());
        assertThrows(NoSuchElementException.class, () -> list.getFirst());
        assertThrows(NoSuchElementException.class, () -> list.getLast());
        assertThrows(NoSuchElementException.class, () -> list.removeFirst());
        assertThrows(NoSuchElementException.class, () -> list.removeLast());
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(0));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(10));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
    }

    private static void assertListEquals(List<?> expected, CircularArrayList<?> actual) {
        assertEquals(expected, actual);
        if (expected.isEmpty()) {
            assertEmpty(actual);
        } else {
            assertEquals(expected.get(0), actual.getFirst());
            assertEquals(expected.get(expected.size() - 1), actual.getLast());
        }
    }

    @Test
    public void testEmpty() {
        CircularArrayList<String> list = new CircularArrayList<>();
        assertEmpty(list);
    }

    @Test
    public void testSequential() {
        Helper<String> helper = new Helper<>();

        helper.addAll("str0", "str1", "str2");
        helper.add("str3");
        helper.add(2, "str4");
        helper.remove(1);
        helper.remove(0);
        helper.removeFirst();
        helper.removeLast();
        helper.remove(0);
        assertEmpty(helper.list);
    }

    @Test
    public void testSequentialExpansion() {
        Helper<String> helper = new Helper<>();
        Random random = new Random(0);
        for (int i = 0; i < 5; i++) {
            helper.add("str" + i);
        }

        for (int i = 5; i < 100; i++) {
            helper.add(random.nextInt(helper.size()) + 1, "str" + i);
        }

        for (int i = 0; i < 100; i++) {
            helper.set(random.nextInt(helper.size()), "new str " + i);
        }

        for (int i = 0; i < 20; i++) {
            helper.remove(random.nextInt(helper.size()));
        }

        for (int i = 0; i < 20; i++) {
            helper.removeFirst();
            helper.removeLast();
        }

        int remaining = helper.size();
        for (int i = 0; i < remaining; i++) {
            helper.removeLast();
        }
    }

    @Test
    public void testLoopback() {
        Helper<String> helper = new Helper<>();

        helper.addAll("str3", "str4", "str5");
        helper.addAll(0, "str0", "str1", "str2");
        helper.remove(1);
        helper.remove(4);
        helper.removeFirst();
        helper.removeLast();
        helper.remove(1);
        helper.remove(0);
        assertEmpty(helper.list);
    }

    @Test
    public void testLoopbackExpansion() {
        Helper<String> helper = new Helper<>();
        Random random = new Random(0);

        for (int i = 5; i < 10; i++) {
            helper.add("str" + i);
        }
        for (int i = 4; i >= 0; i--) {
            helper.add(0, "str" + i);
        }

        for (int i = 10; i < 100; i++) {
            helper.add(random.nextInt(helper.size() + 1), "str" + i);
        }

        for (int i = 0; i < 100; i++) {
            helper.set(random.nextInt(helper.size()), "new str " + i);
        }

        for (int i = 0; i < 20; i++) {
            helper.remove(random.nextInt(helper.size()));
        }

        for (int i = 0; i < 20; i++) {
            helper.removeFirst();
            helper.removeLast();
        }

        int remaining = helper.size();
        for (int i = 0; i < remaining; i++) {
            helper.removeLast();
        }
    }

    @Test
    public void testClear() {
        CircularArrayList<String> list = new CircularArrayList<>();
        list.clear();
        assertEmpty(list);

        for (int i = 0; i < 20; i++) {
            list.add("str" + i);
        }
        list.clear();
        assertEmpty(list);

        for (int i = 10; i < 20; i++) {
            list.add("str" + i);
        }
        for (int i = 9; i >= 0; i--) {
            list.addFirst("str" + i);
        }
        list.clear();
        assertEmpty(list);
    }

    private static final class Helper<E> {
        final List<E> expected;
        final CircularArrayList<E> list;

        Helper() {
            this.expected = new ArrayList<>();
            this.list = new CircularArrayList<>();

            assertStatus();
        }

        Helper(List<E> expected, CircularArrayList<E> list) {
            this.expected = expected;
            this.list = list;

            assertStatus();
        }

        void assertStatus() {
            assertListEquals(expected, list);
        }

        int size() {
            return expected.size();
        }

        void set(int i, E e) {
            assertEquals(expected.set(i, e), list.set(i, e));
            assertStatus();
        }

        void add(E e) {
            expected.add(e);
            list.add(e);
            assertStatus();
        }

        void add(int i, E e) {
            expected.add(i, e);
            list.add(i, e);
            assertStatus();
        }

        @SafeVarargs
        final void addAll(E... values) {
            Collections.addAll(expected, values);
            Collections.addAll(list, values);
            assertStatus();
        }

        @SafeVarargs
        final void addAll(int i, E... values) {
            List<E> valuesList = Arrays.asList(values);
            assertEquals(expected.addAll(i, valuesList), list.addAll(i, valuesList));
            assertStatus();
        }

        void remove(int idx) {
            assertEquals(expected.remove(idx), list.remove(idx));
            assertStatus();
        }

        void removeFirst() {
            assertEquals(expected.remove(0), list.removeFirst());
            assertStatus();
        }

        void removeLast() {
            assertEquals(expected.remove(expected.size() - 1), list.removeLast());
            assertStatus();
        }

        void clear() {
            expected.clear();
            list.clear();
            assertEmpty(list);
        }
    }
}
