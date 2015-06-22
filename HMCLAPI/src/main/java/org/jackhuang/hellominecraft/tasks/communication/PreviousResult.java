/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks.communication;

/**
 *
 * @author hyh
 * @param <T> Task result type
 */
public interface PreviousResult<T> {
    
    T getResult();
    
}
