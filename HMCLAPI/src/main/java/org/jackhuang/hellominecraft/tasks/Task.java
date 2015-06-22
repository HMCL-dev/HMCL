/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks;

import java.util.Collection;

/**
 *
 * @author hyh
 */
public abstract class Task extends ProgressProvider {
    
    /**
     * Run in a new thread(packed in TaskList).
     * @return is task finished sucessfully.
     */
    public abstract boolean executeTask();
    
    /**
     * if this func returns false, TaskList will force abort the thread.
     * run in main thread.
     * @return is aborted.
     */
    public boolean abort() { return false; }
    
    public Throwable getFailReason() { return failReason; }
    protected Throwable failReason = null;
    protected void setFailReason(Throwable s) {
        failReason = s;
    }
    
    protected String tag;
    protected boolean parallelExecuting;
    public Task setTag(String tag) {
        this.tag = tag;
        return this;
    }

    public boolean isParallelExecuting() {
        return parallelExecuting;
    }

    public void setParallelExecuting(boolean parallelExecuting) {
        this.parallelExecuting = parallelExecuting;
    }
    
    public abstract String getInfo();
    
    public Collection<Task> getDependTasks() { return null; }
    public Collection<Task> getAfterTasks() { return null; }
}
