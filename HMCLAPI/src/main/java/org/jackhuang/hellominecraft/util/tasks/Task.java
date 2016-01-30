/*
 * Hello Minecraft!.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.util.tasks;

import java.util.ArrayList;
import java.util.Collection;
import org.jackhuang.hellominecraft.util.logging.HMCLog;

/**
 *
 * @author huangyuhui
 */
public abstract class Task {

    /**
     * Run in a new thread(packed in TaskList).
     */
    public abstract void executeTask() throws Throwable;

    /**
     * if this func returns false, TaskList will force abort the thread. run in
     * main thread.
     *
     * @return is aborted.
     */
    public boolean abort() {
        aborted = true;
        return false;
    }

    protected boolean aborted = false;

    public boolean isAborted() {
        return aborted;
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

    public Collection<? extends Task> getDependTasks() {
        return null;
    }

    public Collection<? extends Task> getAfterTasks() {
        return null;
    }

    protected ProgressProviderListener ppl;

    public Task setProgressProviderListener(ProgressProviderListener p) {
        ppl = p;
        return this;
    }

    public Task after(Task t) {
        return new DoubleTask(this, t);
    }

    public Task before(Task t) {
        return new DoubleTask(t, this);
    }

    public boolean run() {
        try {
            executeTask();
            return true;
        } catch (Throwable t) {
            HMCLog.err("Failed to execute task", t);
            return false;
        }
    }
}
