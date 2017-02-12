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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jackhuang.hellominecraft.api.EventHandler;
import org.jackhuang.hellominecraft.util.log.HMCLog;

/**
 *
 * @author huangyuhui
 */
public class TaskList extends Thread {

    List<Task> taskQueue = Collections.synchronizedList(new LinkedList<>());
    public final EventHandler<EventObject> doneEvent = new EventHandler<>();
    ArrayList<DoingDoneListener<Task>> taskListener = new ArrayList<>();

    int totTask;
    boolean shouldContinue = true;

    public TaskList() {
        setDaemon(true);
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
        CountDownLatch latch;
        AtomicBoolean bool;

        public Invoker(Task task, CountDownLatch latch, AtomicBoolean bool) {
            this.task = task;
            this.latch = latch;
            this.bool = bool;
        }

        @Override
        public void run() {
            try {
                if (!executeTask(task))
                    bool.set(false);
            } finally {
                latch.countDown();
            }
        }

    }

    ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(8);
    HashMap<Invoker, Future<?>> futures = new HashMap<>();
    HashSet<Invoker> invokers = new HashSet<>();

    private boolean processTasks(Collection<? extends Task> c) {
        if (c == null || c.isEmpty())
            return true;
        this.totTask += c.size();
        AtomicBoolean bool = new AtomicBoolean(true);
        CountDownLatch counter = new CountDownLatch(c.size());
        for (Task t2 : c) {
            if (t2 == null) {
                counter.countDown();
                continue;
            }
            Invoker thread = new Invoker(t2, counter, bool);
            invokers.add(thread);
            if (!EXECUTOR_SERVICE.isShutdown() && !EXECUTOR_SERVICE.isTerminated())
                futures.put(thread, EXECUTOR_SERVICE.submit(thread));
        }
        try {
            counter.await();
            return bool.get();
        } catch (InterruptedException ignore) { // this task is canceled, so failed.
            return false;
        }
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
        for (DoingDoneListener<Task> d : taskListener)
            d.onDoing(t, c);
        boolean areDependTasksSucceeded = processTasks(c);

        boolean flag = true;
        try {
            t.executeTask(areDependTasksSucceeded);
        } catch (Throwable e) {
            t.setFailReason(e);
            flag = false;
        }
        if (flag) {
            HMCLog.log((t.isAborted() ? "Task aborted: " : "Task finished: ") + t.getInfo());
            Collection<Task> at = t.getAfterTasks();
            if (at == null)
                at = new HashSet<>();
            for (DoingDoneListener<Task> d : taskListener)
                d.onDone(t, at);
            processTasks(at);
        } else {
            HMCLog.err("Task failed: " + t.getInfo(), t.getFailReason());
            for (DoingDoneListener<Task> d : taskListener)
                d.onFailed(t);
        }
        return flag;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Task List");

        totTask = taskQueue.size();
        while (!taskQueue.isEmpty())
            executeTask(taskQueue.remove(0));
        if (shouldContinue) {
            HMCLog.log("Tasks are successfully finished.");
            doneEvent.fire(new EventObject(this));
        }
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
