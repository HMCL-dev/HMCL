/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

/**
 *
 * @author huangyuhui
 * @param <T> EventArgs
 */
public interface Event<T> {
    boolean call(Object sender, T t);
}
