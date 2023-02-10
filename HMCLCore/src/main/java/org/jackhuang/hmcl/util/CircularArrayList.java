package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.Contract;

import java.util.*;

/**
 * @author Glavo
 */
@SuppressWarnings("unchecked")
public final class CircularArrayList<E> extends AbstractList<E> implements RandomAccess {

    private static final int DEFAULT_CAPACITY = 10;
    private static final Object[] EMPTY_ARRAY = new Object[0];

    private Object[] elements;
    private int begin = -1;
    private int end = 0;

    public CircularArrayList() {
        this.elements = EMPTY_ARRAY;
    }

    public CircularArrayList(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("illegal initialCapacity: " + initialCapacity);
        }

        this.elements = initialCapacity == 0 ? EMPTY_ARRAY : new Object[initialCapacity];
    }

    private static int inc(int i, int capacity) {
        return i + 1 >= capacity ? 0 : i + 1;
    }

    private static int inc(int i, int distance, int capacity) {
        if ((i += distance) - capacity >= 0) {
            i -= capacity;
        }

        return i;
    }

    private static int dec(int i, int capacity) {
        return i - 1 < 0 ? capacity - 1 : i - 1;
    }

    private static int sub(int i, int distance, int capacity) {
        if ((i -= distance) < 0) {
            i += capacity;
        }
        return i;
    }

    private void grow() {
        grow(elements.length + 1);
    }

    private void grow(int minCapacity) {
        final int oldCapacity = elements.length;
        final int size = size();
        final int newCapacity = newCapacity(oldCapacity, minCapacity);

        final Object[] newElements;
        if (size == 0) {
            newElements = new Object[newCapacity];
        } else if (begin < end) {
            newElements = Arrays.copyOf(elements, newCapacity, Object[].class);
        } else {
            newElements = new Object[newCapacity];
            System.arraycopy(elements, begin, newElements, 0, elements.length - begin);
            System.arraycopy(elements, 0, newElements, elements.length - begin, end);
            begin = 0;
            end = size;
        }
        this.elements = newElements;
    }

    private static int newCapacity(int oldCapacity, int minCapacity) {
        return oldCapacity == 0
                ? Math.max(DEFAULT_CAPACITY, minCapacity)
                : Math.max(Math.max(oldCapacity, minCapacity), oldCapacity + (oldCapacity >> 1));
    }

    private static void checkElementIndex(int index, int size) throws IndexOutOfBoundsException {
        if (index < 0 || index >= size) {
            // Optimized for execution by hotspot
            checkElementIndexFailed(index, size);
        }
    }

    @Contract("_, _ -> fail")
    private static void checkElementIndexFailed(int index, int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size(" + size + ") < 0");
        }
        if (index < 0) {
            throw new IndexOutOfBoundsException("index(" + index + ") < 0");
        }
        if (index >= size) {
            throw new IndexOutOfBoundsException("index(" + index + ") >= size(" + size + ")");
        }
        throw new AssertionError();
    }

    private static void checkPositionIndex(int index, int size) throws IndexOutOfBoundsException {
        if (index < 0 || index > size) {
            // Optimized for execution by hotspot
            checkPositionIndexFailed(index, size);
        }
    }

    @Contract("_, _ -> fail")
    private static void checkPositionIndexFailed(int index, int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size(" + size + ") < 0");
        }
        if (index < 0) {
            throw new IndexOutOfBoundsException("index(" + index + ") < 0");
        }
        if (index > size) {
            throw new IndexOutOfBoundsException("index(" + index + ") > size(" + size + ")");
        }
        throw new AssertionError();
    }

    @Override
    public boolean isEmpty() {
        return begin == -1;
    }

    @Override
    public int size() {
        if (isEmpty()) {
            return 0;
        } else if (begin < end) {
            return end - begin;
        } else {
            return elements.length - begin + end;
        }
    }

    @Override
    public E get(int index) {
        if (isEmpty()) {
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        } else if (begin < end) {
            checkElementIndex(index, end - begin);
            return (E) elements[begin + index];
        } else {
            checkElementIndex(index, elements.length - begin + end);
            return (E) elements[inc(begin, index, elements.length)];
        }
    }

    @Override
    public E set(int index, E element) {
        int arrayIndex;
        if (isEmpty()) {
            throw new IndexOutOfBoundsException();
        } else if (begin < end) {
            checkElementIndex(index, end - begin);
            arrayIndex = begin + index;
        } else {
            final int size = elements.length - begin + end;
            checkElementIndex(index, size);
            arrayIndex = inc(begin, index, elements.length);
        }

        E oldValue = (E) elements[arrayIndex];
        elements[arrayIndex] = element;
        return oldValue;
    }

    @Override
    public void add(int index, E element) {
        if (index == 0) {
            addFirst(element);
            return;
        }

        final int oldSize = size();
        if (index == oldSize) {
            addLast(element);
            return;
        }

        checkPositionIndex(index, oldSize);

        if (oldSize == elements.length) {
            grow();
        }

        if (begin < end) {
            final int targetIndex = begin + index;
            if (end < elements.length) {
                System.arraycopy(elements, targetIndex, elements, targetIndex + 1, end - targetIndex);
                end++;
            } else {
                System.arraycopy(elements, begin, elements, begin - 1, targetIndex - begin + 1);
                begin--;
            }
            elements[targetIndex] = element;
        } else {
            int targetIndex = inc(begin, index, elements.length);
            if (targetIndex <= end) {
                System.arraycopy(elements, targetIndex, elements, targetIndex + 1, end - targetIndex);
                elements[targetIndex] = element;
                end++;
            } else {
                System.arraycopy(elements, begin, elements, begin - 1, targetIndex - begin);
                elements[targetIndex - 1] = element;
                begin--;
            }
        }
    }

    @Override
    public E remove(int index) {
        final int oldSize = size();
        checkElementIndex(index, oldSize);

        if (index == 0) {
            return removeFirst();
        }

        if (index == oldSize - 1) {
            return removeLast();
        }

        final Object res;

        if (begin < end) {
            final int targetIndex = begin + index;
            res = elements[targetIndex];
            System.arraycopy(elements, targetIndex + 1, elements, targetIndex, end - targetIndex - 1);
            end--;
        } else {
            final int targetIndex = inc(begin, index, elements.length);
            res = elements[targetIndex];
            if (targetIndex < end) {
                System.arraycopy(elements, targetIndex + 1, elements, targetIndex, end - targetIndex - 1);
                end--;
            } else {
                System.arraycopy(elements, begin, elements, begin + 1, targetIndex - begin);
                begin = inc(begin, elements.length);
            }
        }

        return (E) res;
    }

    @Override
    public void clear() {
        if (isEmpty()) {
            return;
        }

        if (begin < end) {
            Arrays.fill(elements, begin, end, null);
        } else {
            Arrays.fill(elements, 0, end, null);
            Arrays.fill(elements, begin, elements.length, null);
        }

        begin = -1;
        end = 0;
    }

    // Deque

    public void addFirst(E e) {
        final int oldSize = size();
        if (oldSize == elements.length) {
            grow();
        }

        if (oldSize == 0) {
            begin = elements.length - 1;
        } else {
            begin = dec(begin, elements.length);
        }
        elements[begin] = e;
    }

    public void addLast(E e) {
        final int oldSize = size();
        if (oldSize == elements.length) {
            grow();
        }
        elements[end] = e;
        end = inc(end, elements.length);

        if (oldSize == 0) {
            begin = 0;
        }
    }

    public E removeFirst() {
        final int oldSize = size();
        if (oldSize == 0) {
            throw new NoSuchElementException();
        }

        Object res = elements[begin];
        elements[begin] = null;

        if (oldSize == 1) {
            begin = -1;
            end = 0;
        } else {
            begin = inc(begin, elements.length);
        }
        return (E) res;
    }

    public E removeLast() {
        final int oldSize = size();
        if (oldSize == 0) {
            throw new NoSuchElementException();
        }
        final int lastIdx = dec(end, elements.length);
        E res = (E) elements[lastIdx];
        elements[lastIdx] = null;

        if (oldSize == 1) {
            begin = -1;
            end = 0;
        } else {
            end = lastIdx;
        }
        return res;
    }

    public E getFirst() {
        if (isEmpty())
            throw new NoSuchElementException();

        return get(0);
    }

    public E getLast() {
        if (isEmpty())
            throw new NoSuchElementException();

        return get(size() - 1);
    }
}
