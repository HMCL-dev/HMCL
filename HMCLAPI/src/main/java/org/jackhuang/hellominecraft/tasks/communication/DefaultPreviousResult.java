/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks.communication;

/**
 *
 * @author huangyuhui
 */
public class DefaultPreviousResult<T> implements PreviousResult<T>{
    T a;

    public DefaultPreviousResult(T a) {
        this.a = a;
    }
    
    @Override
    public T getResult() {
        return a;
    }
    
}
