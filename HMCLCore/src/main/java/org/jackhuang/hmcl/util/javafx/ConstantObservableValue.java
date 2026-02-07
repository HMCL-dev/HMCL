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

import javafx.beans.InvalidationListener;
import javafx.beans.WeakListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableValue;

import java.util.Objects;
import java.util.function.Predicate;

/// A constant observable value is an observable value that always returns the same value.
///
/// @author Glavo
public interface ConstantObservableValue<T> extends ObservableValue<T> {

    static <T> ObservableObjectValue<T> of(T value) {
        class ConstantObservableObjectValue implements ObservableObjectValue<T>, ConstantObservableValue<T> {
            @Override
            public T get() {
                return value;
            }

            @Override
            public T getValue() {
                return value;
            }

            private ListenerHolder<T> holder;

            private ListenerHolder<T> getHolder() {
                if (holder == null)
                    holder = new ListenerHolder<>();
                return holder;
            }

            @Override
            public void addListener(ChangeListener<? super T> listener) {
                getHolder().addListener(listener);
            }

            @Override
            public void removeListener(ChangeListener<? super T> listener) {
                getHolder().removeListener(listener);
            }

            @Override
            public void addListener(InvalidationListener listener) {
                getHolder().addListener(listener);
            }

            @Override
            public void removeListener(InvalidationListener listener) {
                getHolder().removeListener(listener);
            }
        }

        return new ConstantObservableObjectValue();
    }

    /// Helper class for managing listeners of a constant observable value.
    final class ListenerHolder<T> {

        private ListenerList<InvalidationListener> invalidationListeners;
        private ListenerList<ChangeListener<? super T>> changeListeners;

        public void addListener(InvalidationListener listener) {
            invalidationListeners = ListenerList.add(invalidationListeners, listener);
        }

        public void removeListener(InvalidationListener listener) {
            invalidationListeners = ListenerList.remove(invalidationListeners, listener);
        }

        public void addListener(ChangeListener<? super T> listener) {
            changeListeners = ListenerList.add(changeListeners, listener);
        }

        public void removeListener(ChangeListener<? super T> listener) {
            changeListeners = ListenerList.remove(changeListeners, listener);
        }

        /// Holder of listeners.
        private static final class ListenerList<L> {

            static <L> ListenerList<L> add(ListenerList<L> list, L listener) {
                Objects.requireNonNull(listener, "listener cannot be null");

                if (list == null)
                    return new ListenerList<>(listener, null);

                return new ListenerList<>(listener,
                        removeIf(list, it -> it instanceof WeakListener weakListener && weakListener.wasGarbageCollected()));
            }

            static <L> ListenerList<L> remove(ListenerList<L> list, L listener) {
                Objects.requireNonNull(listener, "listener cannot be null");

                if (list == null)
                    return null;

                return removeIf(list, new Predicate<>() {
                    boolean first = true;

                    @Override
                    public boolean test(L it) {
                        if (first && listener.equals(it)) {
                            first = false;
                            return true;
                        } else
                            return it instanceof WeakListener weakListener && weakListener.wasGarbageCollected();
                    }
                });
            }

            private static <L> ListenerList<L> removeIf(ListenerList<L> list, Predicate<L> predicate) {
                if (list == null)
                    return null;

                ListenerList<L> current = list;
                while (current.next != null) {
                    ListenerList<L> next = current.next;

                    if (predicate.test(next.head)) {
                        current.next = next.next;
                        continue;
                    }
                    current = next;
                }
                return list;
            }

            private final L head;
            private ListenerList<L> next;

            private ListenerList(L head, ListenerList<L> next) {
                this.head = head;
                this.next = next;
            }
        }
    }
}
