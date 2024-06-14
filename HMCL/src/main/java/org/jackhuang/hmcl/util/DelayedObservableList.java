package org.jackhuang.hmcl.util;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class DelayedObservableList<E> implements ObservableList<E> {
    private static final class DelayedDaemonThread<E> extends Thread {
        private final int delay;

        private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();

        private final WeakReference<DelayedObservableList<E>> reference;

        public DelayedDaemonThread(DelayedObservableList<E> list, int delay) {
            super("DelayedDaemonThread #" + System.identityHashCode(list));
            this.reference = new WeakReference<>(list);
            this.delay = delay;
            setDaemon(true);
        }

        public void pushTask(Runnable task) {
            if (!this.tasks.offer(task)) {
                throw new IllegalStateException("Task queue is full. Daemon State: " + this.getState());
            }
        }

        @Override
        public void run() {
            while (true) {
                Runnable head = tasks.peek();

                if (head != null) {
                    Platform.runLater(() -> {
                        while (true) {
                            Runnable task = tasks.poll();
                            if (task == null) {
                                return;
                            }
                            task.run();
                        }
                    });
                }

                try {
                    Thread.sleep(this.delay);
                } catch (InterruptedException e) {
                    return;
                }

                if (reference.get() == null) {
                    return;
                }
            }
        }
    }

    private static final class DelayedListChangeListener<E> implements ListChangeListener<E> {
        private final ListChangeListener<? super E> delegate;

        private final DelayedDaemonThread<E> executor;

        public DelayedListChangeListener(ListChangeListener<? super E> delegate, DelayedDaemonThread<E> executor) {
            this.delegate = delegate;
            this.executor = executor;
        }

        @Override
        public void onChanged(Change<? extends E> c) {
            executor.pushTask(() -> delegate.onChanged(c));
        }
    }

    private static final class DelayedInvalidationListener<E> implements InvalidationListener {
        private final InvalidationListener delegate;

        private final DelayedDaemonThread<E> executor;

        public DelayedInvalidationListener(InvalidationListener delegate, DelayedDaemonThread<E> executor) {
            this.delegate = delegate;
            this.executor = executor;
        }

        @Override
        public void invalidated(Observable observable) {
            executor.pushTask(() -> delegate.invalidated(observable));
        }
    }

    private final ObservableList<E> delegate;

    private final DelayedDaemonThread<E> executor;

    private final Map<ListChangeListener<? super E>, DelayedListChangeListener<? super E>> listChangeListeners = new IdentityHashMap<>();

    private final Map<InvalidationListener, DelayedInvalidationListener<E>> invalidationListeners = new IdentityHashMap<>();

    public DelayedObservableList(ObservableList<E> delegate, int delay) {
        this.delegate = delegate;
        this.executor = new DelayedDaemonThread<>(this, delay);

        this.executor.start();
    }

    @Override
    public void addListener(ListChangeListener<? super E> listener) {
        if (listChangeListeners.get(listener) != null) {
            return;
        }

        DelayedListChangeListener<? super E> delegateListener = new DelayedListChangeListener<>(listener, executor);
        listChangeListeners.put(listener, delegateListener);
        delegate.addListener(delegateListener);
    }

    @Override
    public void removeListener(ListChangeListener<? super E> listener) {
        DelayedListChangeListener<? super E> delegateListener = listChangeListeners.remove(listener);
        if (delegateListener == null) {
            return;
        }

        delegate.removeListener(delegateListener);
    }

    @Override
    public void addListener(InvalidationListener listener) {
        if (invalidationListeners.get(listener) != null) {
            return;
        }

        DelayedInvalidationListener<E> delegateListener = new DelayedInvalidationListener<>(listener, executor);
        invalidationListeners.put(listener, delegateListener);
        delegate.addListener(delegateListener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        DelayedInvalidationListener<E> delegateListener = invalidationListeners.remove(listener);
        if (delegateListener == null) {
            return;
        }

        delegate.removeListener(delegateListener);
    }

    @SafeVarargs
    @Override
    public final boolean addAll(E... elements) {
        return delegate.addAll(elements);
    }

    @SafeVarargs
    @Override
    public final boolean setAll(E... elements) {
        return delegate.setAll(elements);
    }

    @Override
    public boolean setAll(Collection<? extends E> col) {
        return delegate.setAll(col);
    }

    @SafeVarargs
    @Override
    public final boolean removeAll(E... elements) {
        return delegate.removeAll(elements);
    }

    @SafeVarargs
    @Override
    public final boolean retainAll(E... elements) {
        return delegate.retainAll(elements);
    }

    @Override
    public void remove(int from, int to) {
        delegate.remove(from, to);
    }

    @Override
    public FilteredList<E> filtered(Predicate<E> predicate) {
        return delegate.filtered(predicate);
    }

    @Override
    public SortedList<E> sorted(Comparator<E> comparator) {
        return delegate.sorted(comparator);
    }

    @Override
    public SortedList<E> sorted() {
        return delegate.sorted();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @NotNull
    @Override
    public Iterator<E> iterator() {
        return delegate.iterator();
    }

    @NotNull
    @Override
    public Object @NotNull [] toArray() {
        return delegate.toArray();
    }

    @NotNull
    @Override
    public <T> T @NotNull [] toArray(@NotNull T @NotNull [] a) {
        return delegate.toArray(a);
    }

    @Override
    public boolean add(E e) {
        return delegate.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
        return delegate.addAll(c);
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends E> c) {
        return delegate.addAll(index, c);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        return delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return delegate.retainAll(c);
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        delegate.replaceAll(operator);
    }

    @Override
    public void sort(Comparator<? super E> c) {
        delegate.sort(c);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return delegate.equals(((DelayedObservableList<?>) o).delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public E get(int index) {
        return delegate.get(index);
    }

    @Override
    public E set(int index, E element) {
        return delegate.set(index, element);
    }

    @Override
    public void add(int index, E element) {
        delegate.add(index, element);
    }

    @Override
    public E remove(int index) {
        return delegate.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return delegate.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return delegate.lastIndexOf(o);
    }

    @NotNull
    @Override
    public ListIterator<E> listIterator() {
        return delegate.listIterator();
    }

    @NotNull
    @Override
    public ListIterator<E> listIterator(int index) {
        return delegate.listIterator(index);
    }

    @NotNull
    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return delegate.subList(fromIndex, toIndex);
    }

    @Override
    public Spliterator<E> spliterator() {
        return delegate.spliterator();
    }
}
