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

import java.util.ArrayList;
import org.jackhuang.hellominecraft.util.func.Consumer;

/**
 *
 * @author huangyuhui
 * @param <T> EventArgs
 */
public class EventHandler<T> {

    ArrayList<Object> events = new ArrayList<>();
    Object sender;

    public EventHandler(Object sender) {
        this.sender = sender;
    }

    public void register(Event<T> t) {
        if (!events.contains(t))
            events.add(t);
    }

    public void register(Consumer<T> t) {
        if (!events.contains(t))
            events.add(t);
    }

    public void register(Runnable t) {
        if (!events.contains(t))
            events.add(t);
    }

    public void unregister(Event<T> t) {
        events.remove(t);
    }

    public void unregister(Consumer<T> t) {
        events.remove(t);
    }

    public void unregister(Runnable t) {
        events.remove(t);
    }

    public boolean execute(T x) {
        boolean flag = true;
        for (Object t : events)
            if (t instanceof Event) {
                if (!((Event) t).call(sender, x))
                    flag = false;
            } else if (t instanceof Consumer)
                ((Consumer) t).accept(x);
            else if (t instanceof Runnable)
                ((Runnable) t).run();
        return flag;
    }

}
