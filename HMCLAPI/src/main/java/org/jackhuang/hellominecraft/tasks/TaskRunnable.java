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
public class TaskRunnable extends TaskInfo {
    private final Runnable r;
    public TaskRunnable(String info, Runnable r) {
        super(info);
        this.r = r;
    }

    @Override
    public boolean executeTask() {
        try {
            r.run();
            return true;
        } catch(Throwable t) {
            setFailReason(t);
            return false;
        }
    }
    
}
