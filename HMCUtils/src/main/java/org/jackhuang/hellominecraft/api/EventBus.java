/*
 * Hello Minecraft! Launcher.
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
package org.jackhuang.hellominecraft.api;

import java.util.EventObject;
import java.util.HashMap;

/**
 *
 * @author huang
 */
public class EventBus {

    HashMap<Class, EventHandler> events = new HashMap<>();

    public EventBus() {
    }

    public <T extends EventObject> EventHandler<T> channel(Class<T> classOfT) {
        if (!events.containsKey(classOfT))
            events.put(classOfT, new EventHandler<>());
        return events.get(classOfT);
    }
    
    public void fireChannel(EventObject obj) {
        channel((Class<EventObject>) obj.getClass()).fire(obj);
    }
    
    public boolean fireChannelResulted(ResultedEvent obj) {
        return channel((Class<ResultedEvent>) obj.getClass()).fireResulted(obj);
    }
    
}
