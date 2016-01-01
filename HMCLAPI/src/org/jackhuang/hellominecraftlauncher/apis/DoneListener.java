/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis;

/**
 * 许多类需要此listener作为事件触发器
 * @author hyh
 */
public interface DoneListener<V, V2> {
    void onDone(V value, V2 value2);
}
