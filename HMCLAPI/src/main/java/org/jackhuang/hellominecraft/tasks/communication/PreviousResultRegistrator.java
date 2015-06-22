/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks.communication;

import org.jackhuang.hellominecraft.tasks.Task;

/**
 *
 * @author hyh
 * @param <T> Previous task result type
 */
public interface PreviousResultRegistrator<T> {
    
    /**
     * 
     * @param pr previous task handler
     * @return task self instance
     */
    Task registerPreviousResult(PreviousResult<T> pr);
}
