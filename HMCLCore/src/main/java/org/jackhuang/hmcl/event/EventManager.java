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

import org.jackhuang.hmcl.util.SimpleMultimap;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 *
 * @author huangyuhui
 */
public final class EventManager<T extends Event> {

    private final SimpleMultimap<EventPriority, Consumer<T>, CopyOnWriteArraySet<Consumer<T>>> handlers
            = new SimpleMultimap<>(() -> new EnumMap<>(EventPriority.class), CopyOnWriteArraySet::new);
    private volatile Consumer<T> compiled;

    public Consumer<T> registerWeak(Consumer<T> consumer) {
        register(new WeakListener(consumer));
        return consumer;
    }

    public Consumer<T> registerWeak(Consumer<T> consumer, EventPriority priority) {
        register(new WeakListener(consumer), priority);
        return consumer;
    }

    public void register(Consumer<T> consumer) {
        register(consumer, EventPriority.NORMAL);
    }

    public synchronized void register(Consumer<T> consumer, EventPriority priority) {
        if (handlers.get(priority).contains(consumer)) {
            return;
        }
        handlers.put(priority, consumer);
        compiled = null;
    }

    public void register(Runnable runnable) {
        register(t -> runnable.run());
    }

    public void register(Runnable runnable, EventPriority priority) {
        register(t -> runnable.run(), priority);
    }

    public synchronized Event.Result fireEvent(T event) {
        Consumer<T> compiled = this.compiled;
        if (compiled == null) {
            synchronized (this) {
                if (this.compiled == null) {
                    Consumer<T>[] handlers = this.handlers
                        .keys()
                        .stream()
                        .sorted(Comparator.comparingInt(Enum::ordinal))
                        .map(this.handlers::get)
                        .flatMap(Collection::stream)
                        .toArray((IntFunction<Consumer<T>[]>) Consumer[]::new);
                    compiled = compileHandlers(handlers);
                    this.compiled = compiled;
                }
            }
        }

        compiled.accept(event);

        if (event.hasResult())
            return event.getResult();
        else
            return Event.Result.DEFAULT;
    }

    public synchronized void unregister(Consumer<T> consumer) {
        handlers.removeValue(consumer);
        compiled = null;
    }

    private static <T> Consumer<T> compileHandlers(Consumer<T>[] handlers) {
        switch (handlers.length) {
            case 0:
                return (ignored) -> {};
            case 1:
                return handlers[0];
            case 2:
                return handlers[0].andThen(handlers[1]);
            case 3:
                Consumer<T> handler1 = handlers[0];
                Consumer<T> handler2 = handlers[1];
                Consumer<T> handler3 = handlers[2];
                return t -> {
                    handler1.accept(t);
                    handler2.accept(t);
                    handler3.accept(t);
                };
            default:
                return (t) -> {
                    for (Consumer<T> handler : handlers) {
                        handler.accept(t);
                    }
                };
        }
    }

    private class WeakListener implements Consumer<T> {
        private final WeakReference<Consumer<T>> ref;

        public WeakListener(Consumer<T> listener) {
            this.ref = new WeakReference<>(listener);
        }

        @Override
        public void accept(T t) {
            Consumer<T> listener = ref.get();
            if (listener == null) {
                unregister(this);
            } else {
                listener.accept(t);
            }
        }
    }
}
