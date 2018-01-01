/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.event;

import java.util.EnumMap;
import java.util.EventObject;
import java.util.HashSet;
import java.util.function.Consumer;
import org.jackhuang.hmcl.task.Scheduler;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.SimpleMultimap;

/**
 *
 * @author huangyuhui
 */
public final class EventManager<T extends EventObject> {

    private final Scheduler scheduler;
    private final SimpleMultimap<EventPriority, Consumer<T>> handlers
            = new SimpleMultimap<>(() -> new EnumMap<>(EventPriority.class), HashSet::new);
    private final SimpleMultimap<EventPriority, Runnable> handlers2
            = new SimpleMultimap<>(() -> new EnumMap<>(EventPriority.class), HashSet::new);

    public EventManager() {
        this(Schedulers.immediate());
    }

    public EventManager(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void register(Consumer<T> consumer) {
        register(consumer, EventPriority.NORMAL);
    }

    public void register(Consumer<T> consumer, EventPriority priority) {
        if (!handlers.get(priority).contains(consumer))
            handlers.put(priority, consumer);
    }

    public void register(Runnable runnable) {
        register(runnable, EventPriority.NORMAL);
    }

    public void register(Runnable runnable, EventPriority priority) {
        if (!handlers2.get(priority).contains(runnable))
            handlers2.put(priority, runnable);
    }

    public void unregister(Consumer<T> consumer) {
        handlers.removeValue(consumer);
    }

    public void unregister(Runnable runnable) {
        handlers2.removeValue(runnable);
    }

    public void fireEvent(T event) {
        scheduler.schedule(() -> {
            for (EventPriority priority : EventPriority.values()) {
                for (Consumer<T> handler : handlers.get(priority))
                    handler.accept(event);
                for (Runnable runnable : handlers2.get(priority))
                    runnable.run();
            }
        });
    }

}
