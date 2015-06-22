/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks;

/**
 *
 * @author hyh
 */
public interface DoingDoneListener<K> {
    /**
     * Task done.
     * @param k 
     */
    void onDone(K k);
    /**
     * Before task executing.
     * @param k 
     */
    void onDoing(K k);
    /**
     * Task failed.
     * @param k 
     */
    void onFailed(K k);
}
