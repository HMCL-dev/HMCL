/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import java.util.HashSet;

/**
 *
 * @author huangyuhui
 * @param <T> EventArgs
 */
public class EventHandler<T> {
    HashSet<Event<T>> handlers;
    Object sender;
    
    public EventHandler(Object sender) {
        handlers = new HashSet<>();
        this.sender = sender;
    }
    
    public void register(Event<T> t) {
        handlers.add(t);
    }
    
    public void unregister(Event<T> t) {
        handlers.remove(t);
    }
    
    public boolean execute(T x) {
        boolean flag = true;
        for(Event<T> t : handlers)
            if(!t.call(sender, x)) flag = false;
        return flag;
    }
    
}
