/*
 * Hello Minecraft! Launcher.
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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.jackhuang.hellominecraft.util.logging.HMCLog;

/**
 *
 * @author huangyuhui
 */
public class TaskList extends Thread {

    List<Task> taskQueue = Collections.synchronizedList(new LinkedList<>());
    ArrayList<Runnable> allDone = new ArrayList();
    ArrayList<DoingDoneListener<Task>> taskListener = new ArrayList();

    int totTask;
    boolean shouldContinue = true;

    public TaskList() {
        setDaemon(true);
    }

    public void clean() {
        shouldContinue = true;
        taskQueue.clear();
    }

    public void addAllDoneListener(Runnable l) {
        allDone.add(l);
    }

    public void addTaskListener(DoingDoneListener<Task> l) {
        taskListener.add(l);
    }

    public void addTask(Task task) {
        taskQueue.add(task);
    }

    public int taskCount() {
        return totTask;
    }

    private class InvokeThread extends Thread {

        Task task;
        Set<InvokeThread> s;

        public InvokeThread(Task task, Set<InvokeThread> ss) {
            this.task = task;
            s = ss;
            setDaemon(true);
        }

        @Override
        public void run() {
            executeTask(task);
            s.remove(this);
            THREAD_POOL.remove(this);
        }

    }

    static final Set<InvokeThread> THREAD_POOL = Collections.synchronizedSet(new HashSet<InvokeThread>());
    static final Set<Task> TASK_POOL = Collections.synchronizedSet(new HashSet<Task>());

    private void processTasks(Collection<? extends Task> c) {
        if (c == null)
            return;
        this.totTask += c.size();
        Set<InvokeThread> runningThread = Collections.synchronizedSet(new HashSet<InvokeThread>());
        for (Task t2 : c) {
            t2.setParallelExecuting(true);
            InvokeThread thread = new InvokeThread(t2, runningThread);
            THREAD_POOL.add(thread);
            runningThread.add(thread);
            thread.start();
        }
        while (!runningThread.isEmpty())
            try {
                if (this.isInterrupted())
                    return;
                Thread.sleep(1);
            } catch (InterruptedException ignore) {
            }

    }

    private void executeTask(Task t) {
        if (!shouldContinue || t == null)
            return;
        processTasks(t.getDependTasks());

        HMCLog.log("Executing task: " + t.getInfo());
        for (DoingDoneListener<Task> d : taskListener)
            d.onDoing(t);
        for (DoingDoneListener<Task> d : t.getTaskListeners())
            d.onDoing(t);

        boolean flag = true;
        try {
            t.executeTask();
        } catch (Throwable e) {
            t.setFailReason(e);
            flag = false;
        }
        if (flag) {
            HMCLog.log((t.isAborted() ? "Task aborted: " : "Task finished: ") + t.getInfo());
            for (DoingDoneListener<Task> d : taskListener)
                d.onDone(t);
            for (DoingDoneListener<Task> d : t.getTaskListeners())
                d.onDone(t);
            processTasks(t.getAfterTasks());
        } else {
            HMCLog.err("Task failed: " + t.getInfo(), t.getFailReason());
            for (DoingDoneListener<Task> d : taskListener)
                d.onFailed(t);
            for (DoingDoneListener<Task> d : t.getTaskListeners())
                d.onFailed(t);
        }
    }

    @Override
    public void run() {
        Thread.currentThread().setName("TaskList");

        THREAD_POOL.clear();
        totTask = taskQueue.size();
        while (!taskQueue.isEmpty())
            executeTask(taskQueue.remove(0));
        if (shouldContinue)
            for (Runnable d : allDone)
                d.run();
    }

    public boolean isEmpty() {
        return taskQueue.isEmpty();
    }

    public void abort() {
        shouldContinue = false;
        while (!THREAD_POOL.isEmpty())
            synchronized (THREAD_POOL) {
                InvokeThread it = THREAD_POOL.iterator().next();
                if (!it.task.abort())
                    it.interrupt();
                THREAD_POOL.remove(it);
            }
        this.interrupt();
    }

}
