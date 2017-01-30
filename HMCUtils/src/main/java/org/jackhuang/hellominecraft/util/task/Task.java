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
package org.jackhuang.hellominecraft.util.task;

import java.util.Collection;

/**
 *
 * @author huangyuhui
 */
public abstract class Task {

    /**
     * Run in a new thread(packed in TaskList).
     * 
     * @param areDependTasksSucceeded Would be true if all of tasks which this task depends on have succeed.
     * @throws java.lang.Throwable If a task throws an exception, this task will be marked as `failed`.
     */
    public abstract void executeTask(boolean areDependTasksSucceeded) throws Throwable;

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
    
    protected boolean hidden = false;
    
    public boolean isHidden() {
        return hidden;
    }

    public Throwable getFailReason() {
        return failReason;
    }
    protected Throwable failReason = null;

    /**
     * This method can be only invoked by TaskList.
     * @param s what the `executeTask` throws.
     */
    protected void setFailReason(Throwable s) {
        failReason = s;
    }

    protected String tag;

    /**
     * For FileDownloadTask: info replacement.
     * @return 
     */
    public String getTag() {
        return tag;
    }
    
    public Task setTag(String tag) {
        this.tag = tag;
        return this;
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

    public Task with(Task t) {
        return new DoubleTask(this, t);
    }

    public void runWithException() throws Throwable {
        Collection<Task> c = getDependTasks();
        if (c != null)
            for (Task t : c)
                t.runWithException();
        executeTask(true);
        c = getAfterTasks();
        if (c != null)
            for (Task t : c)
                t.runWithException();
    }
}
