/*
 * Hello Minecraft!.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.util;

import java.util.HashSet;
import org.jackhuang.hellominecraft.util.func.Consumer;

/**
 *
 * @author huangyuhui
 * @param <T> EventArgs
 */
public class EventHandler<T> {

    HashSet<Event<T>> handlers = new HashSet<>();
    HashSet<Consumer<T>> consumers = new HashSet<>();
    HashSet<Runnable> runnables = new HashSet<>();
    Object sender;

    public EventHandler(Object sender) {
        this.sender = sender;
    }

    public void register(Event<T> t) {
        handlers.add(t);
    }

    public void register(Consumer<T> t) {
        consumers.add(t);
    }

    public void register(Runnable t) {
        runnables.add(t);
    }

    public void unregister(Event<T> t) {
        handlers.remove(t);
    }

    public void unregister(Consumer<T> t) {
        consumers.remove(t);
    }

    public void unregister(Runnable t) {
        runnables.remove(t);
    }

    public boolean execute(T x) {
        boolean flag = true;
        for (Event<T> t : handlers)
            if (!t.call(sender, x))
                flag = false;
        for (Consumer<T> t : consumers)
            t.accept(x);
        for (Runnable t : runnables)
            t.run();
        return flag;
    }

}
