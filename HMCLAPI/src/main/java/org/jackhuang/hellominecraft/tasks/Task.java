/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.tasks;

import java.util.ArrayList;
import java.util.Collection;

/**
 *
 * @author huangyuhui
 */
public abstract class Task {

    /**
     * Run in a new thread(packed in TaskList).
     *
     * @return is task finished sucessfully.
     */
    public abstract boolean executeTask();

    /**
     * if this func returns false, TaskList will force abort the thread. run in
     * main thread.
     *
     * @return is aborted.
     */
    public boolean abort() {
        return false;
    }

    public Throwable getFailReason() {
        return failReason;
    }
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
    
    ArrayList<DoingDoneListener<Task>> taskListener = new ArrayList();

    public Task addTaskListener(DoingDoneListener<Task> l) {
        taskListener.add(l);
        return this;
    }

    public ArrayList<DoingDoneListener<Task>> getTaskListeners() {
        return taskListener;
    }

    public abstract String getInfo();

    public Collection<Task> getDependTasks() {
        return null;
    }

    public Collection<Task> getAfterTasks() {
        return null;
    }
    
    protected ProgressProviderListener ppl;

    public Task setProgressProviderListener(ProgressProviderListener p) {
        ppl = p;
        return this;
    }
}
