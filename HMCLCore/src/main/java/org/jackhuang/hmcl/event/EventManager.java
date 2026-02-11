/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.event;

import org.jetbrains.annotations.Contract;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/// @author huangyuhui
public final class EventManager<T extends Event> {

    private static final int PRIORITY_COUNT = EventPriority.values().length;

    private final ReentrantLock lock = new ReentrantLock();
    @SuppressWarnings("unchecked")
    private final CopyOnWriteArrayList<Consumer<T>>[] allHandlers = (CopyOnWriteArrayList<Consumer<T>>[]) new CopyOnWriteArrayList<?>[PRIORITY_COUNT];

    @Contract("_ -> param1")
    public Consumer<T> registerWeak(Consumer<T> consumer) {
        register(new WeakListener<>(new WeakReference<>(consumer)));
        return consumer;
    }

    @Contract("_, _ -> param1")
    public Consumer<T> registerWeak(Consumer<T> consumer, EventPriority priority) {
        register(new WeakListener<>(new WeakReference<>(consumer)), priority);
        return consumer;
    }

    public void register(Consumer<T> consumer) {
        register(consumer, EventPriority.NORMAL);
    }

    public void register(Consumer<T> consumer, EventPriority priority) {
        lock.lock();
        try {
            var handlers = allHandlers[priority.ordinal()];
            if (handlers == null) {
                handlers = new CopyOnWriteArrayList<>();
                allHandlers[priority.ordinal()] = handlers;
            }
            handlers.add(consumer);
        } finally {
            lock.unlock();
        }
    }

    public void register(Runnable runnable) {
        register(t -> runnable.run());
    }

    public void register(Runnable runnable, EventPriority priority) {
        register(t -> runnable.run(), priority);
    }

    public Event.Result fireEvent(T event) {
        lock.lock();
        try {
            for (var handlers : allHandlers) {
                if (handlers != null) {
                    for (Consumer<T> handler : handlers) {
                        if (handler instanceof WeakListener<T> weakListener) {
                            Consumer<T> consumer = weakListener.ref.get();
                            if (consumer != null) {
                                consumer.accept(event);
                            } else {
                                handlers.remove(weakListener);
                            }
                        } else {
                            handler.accept(event);
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }

        return event.hasResult() ? event.getResult() : Event.Result.DEFAULT;
    }

    private record WeakListener<T>(WeakReference<Consumer<T>> ref) implements Consumer<T> {
        @Override
        public void accept(T t) {
            Consumer<T> listener = ref.get();
            if (listener != null)
                listener.accept(t);
        }
    }
}
