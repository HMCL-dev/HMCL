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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jackhuang.hellominecraft.utils.functions.NonConsumer;
import org.jackhuang.hellominecraft.HMCLog;

/**
 *
 * @author hyh
 */
public class TaskList extends Thread {

    List<Task> taskQueue = Collections.synchronizedList(new ArrayList());
    ArrayList<NonConsumer> allDone = new ArrayList();
    ArrayList<DoingDoneListener<Task>> taskListener = new ArrayList();

    int totTask = 0;
    boolean shouldContinue = true;

    public TaskList() {
    }

    public void clean() {
        shouldContinue = true;
        totTask = 0;
        taskQueue.clear();
    }

    public void addAllDoneListener(NonConsumer l) {
        allDone.add(l);
    }

    public void addTaskListener(DoingDoneListener<Task> l) {
        taskListener.add(l);
    }

    public void addTask(Task task) {
        taskQueue.add(task);
        totTask++;
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
        }

        @Override
        public void run() {
            executeTask(task);
            s.remove(this);
            threadPool.remove(this);
        }

    }

    static final Set<InvokeThread> threadPool = Collections.synchronizedSet(new HashSet<InvokeThread>());
    static final Set<Task> taskPool = Collections.synchronizedSet(new HashSet<Task>());

    private void processTasks(Collection<Task> c) {
        if (c == null) {
            return;
        }
        this.totTask += c.size();
        Set<InvokeThread> runningThread = Collections.synchronizedSet(new HashSet<InvokeThread>());
        for (Task t2 : c) {
            t2.setParallelExecuting(true);
            InvokeThread thread = new InvokeThread(t2, runningThread);
            threadPool.add(thread);
            runningThread.add(thread);
            thread.start();
        }
        while (!runningThread.isEmpty()) {
            try {
                if(this.isInterrupted()) return;
                Thread.sleep(1);
            } catch (InterruptedException ex) {
                HMCLog.warn("Failed to sleep task thread", ex);
            }
        }
        
    }

    private void executeTask(Task t) {
        if (!shouldContinue || t == null) {
            return;
        }
        processTasks(t.getDependTasks());

        HMCLog.log("Executing task: " + t.getInfo());
        for (DoingDoneListener<Task> d : taskListener) {
            d.onDoing(t);
        }

        if (t.executeTask()) {
            HMCLog.log("Task finished: " + t.getInfo());
            for (DoingDoneListener<Task> d : taskListener) {
                d.onDone(t);
            }
            processTasks(t.getAfterTasks());
        } else {
            HMCLog.err("Task failed: " + t.getInfo(), t.getFailReason());
            for (DoingDoneListener<Task> d : taskListener) {
                d.onFailed(t);
            }
        }
    }

    @Override
    public void run() {
        Thread.currentThread().setName("TaskList");
        
        threadPool.clear();
        for (Task taskQueue1 : taskQueue)
            executeTask(taskQueue1);
        if (shouldContinue)
            for (NonConsumer d : allDone)
                d.onDone();
    }

    public boolean isEmpty() {
        return taskQueue.isEmpty();
    }

    public void abort() {
        shouldContinue = false;
        while(!threadPool.isEmpty())
            synchronized(threadPool) {
                InvokeThread it = threadPool.iterator().next();
                if(!it.task.abort()) it.interrupt();
                threadPool.remove(it);
            }
        this.interrupt();
    }

}
