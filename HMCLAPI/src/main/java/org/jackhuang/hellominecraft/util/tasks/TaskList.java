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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private class Invoker implements Runnable {

        Task task;
        Set<Invoker> s;
        AtomicBoolean bool;

        public Invoker(Task task, Set<Invoker> ss, AtomicBoolean bool) {
            this.task = task;
            s = ss;
            this.bool = bool;
        }

        @Override
        public void run() {
            if (!executeTask(task))
                bool.set(false);
            s.remove(this);
        }

    }

    ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(64);
    HashMap<Invoker, Future<?>> futures = new HashMap<>();
    HashSet<Invoker> invokers = new HashSet<>();

    private boolean processTasks(Collection<? extends Task> c) {
        if (c == null || c.isEmpty())
            return true;
        this.totTask += c.size();
        AtomicBoolean bool = new AtomicBoolean(true);
        Set<Invoker> runningThread = Collections.synchronizedSet(new HashSet<Invoker>());
        for (Task t2 : c) {
            t2.setParallelExecuting(true);
            Invoker thread = new Invoker(t2, runningThread, bool);
            runningThread.add(thread);
            invokers.add(thread);
            if (!EXECUTOR_SERVICE.isShutdown() && !EXECUTOR_SERVICE.isTerminated())
                futures.put(thread, EXECUTOR_SERVICE.submit(thread));
        }
        while (!runningThread.isEmpty())
            try {
                if (this.isInterrupted())
                    return false;
                Thread.sleep(1);
            } catch (InterruptedException ignore) {
            }
        return bool.get();
    }

    private boolean executeTask(Task t) {
        if (!shouldContinue)
            return false;
        if (t == null)
            return true;

        Collection<Task> c = t.getDependTasks();
        if (c == null)
            c = new HashSet<>();
        HMCLog.log("Executing task: " + t.getInfo());
        for (DoingDoneListener<Task> d : t.getTaskListeners())
            d.onDoing(t, c);
        for (DoingDoneListener<Task> d : taskListener)
            d.onDoing(t, c);
        t.areDependTasksSucceeded = processTasks(c);

        boolean flag = true;
        try {
            t.executeTask();
        } catch (Throwable e) {
            t.setFailReason(e);
            flag = false;
        }
        if (flag) {
            HMCLog.log((t.isAborted() ? "Task aborted: " : "Task finished: ") + t.getInfo());
            Collection<Task> at = t.getAfterTasks();
            if (at == null)
                at = new HashSet<>();
            for (DoingDoneListener<Task> d : t.getTaskListeners())
                d.onDone(t, at);
            for (DoingDoneListener<Task> d : taskListener)
                d.onDone(t, at);
            processTasks(at);
        } else {
            HMCLog.err("Task failed: " + t.getInfo(), t.getFailReason());
            for (DoingDoneListener<Task> d : taskListener)
                d.onFailed(t);
            for (DoingDoneListener<Task> d : t.getTaskListeners())
                d.onFailed(t);
        }
        return flag;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("TaskList");

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
        final HashSet<Invoker> in = this.invokers;
        EXECUTOR_SERVICE.shutdown();
        while (!in.isEmpty())
            synchronized (in) {
                Invoker it = in.iterator().next();
                if (!it.task.abort() && futures.get(it) != null)
                    futures.get(it).cancel(true);
                in.remove(it);
            }
        this.interrupt();
    }

}
