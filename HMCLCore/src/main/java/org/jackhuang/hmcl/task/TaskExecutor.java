/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.util.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 *
 * @author huangyuhui
 */
public final class TaskExecutor {

    private final Task firstTask;
    private final List<TaskListener> taskListeners = new LinkedList<>();
    private boolean canceled = false;
    private Exception lastException;
    private final AtomicInteger totTask = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<Future<?>> workerQueue = new ConcurrentLinkedQueue<>();
    private final AutoTypingMap<String> variables = new AutoTypingMap<>(new HashMap<>());
    private Scheduler scheduler = Schedulers.newThread();

    public TaskExecutor(Task task) {
        this.firstTask = task;
    }

    public void addTaskListener(TaskListener taskListener) {
        taskListeners.add(taskListener);
    }

    public boolean isCanceled() {
        return canceled;
    }

    public Exception getLastException() {
        return lastException;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    public TaskExecutor start() {
        taskListeners.forEach(TaskListener::onStart);
        workerQueue.add(scheduler.schedule(() -> {
            boolean flag = executeTasks(Collections.singleton(firstTask));
            taskListeners.forEach(it -> it.onStop(flag, this));
        }));
        return this;
    }

    public boolean test() {
        taskListeners.forEach(TaskListener::onStart);
        AtomicBoolean flag = new AtomicBoolean(true);
        Future<?> future = scheduler.schedule(() -> {
            flag.set(executeTasks(Collections.singleton(firstTask)));
            taskListeners.forEach(it -> it.onStop(flag.get(), this));
        });
        workerQueue.add(future);
        Lang.invoke((ExceptionalSupplier<?, Exception>) future::get);
        return flag.get();
    }

    /**
     * Cancel the subscription ant interrupt all tasks.
     */
    public synchronized void cancel() {
        canceled = true;

        while (!workerQueue.isEmpty()) {
            Future<?> future = workerQueue.poll();
            if (future != null) {
                future.cancel(true);
                System.out.println("Canceled " + future);
            }
        }
    }

    private boolean executeTasks(Collection<? extends Task> tasks) throws InterruptedException {
        if (tasks.isEmpty())
            return true;

        totTask.addAndGet(tasks.size());
        AtomicBoolean success = new AtomicBoolean(true);
        CountDownLatch latch = new CountDownLatch(tasks.size());
        for (Task task : tasks) {
            if (canceled)
                return false;
            Invoker invoker = new Invoker(task, latch, success);
            try {
                Future<?> future = scheduler.schedule(invoker);
                if (future != null)
                    workerQueue.add(future);
            } catch (RejectedExecutionException e) {
                throw new InterruptedException();
            }
        }

        if (canceled)
            return false;

        try {
            latch.await();
        } catch (InterruptedException e) {
            return false;
        }
        return success.get() && !canceled;
    }

    private boolean executeTask(Task task) {
        if (canceled) {
            task.setState(Task.TaskState.FAILED);
            return false;
        }

        task.setState(Task.TaskState.READY);

        if (task.getSignificance().shouldLog())
            Logging.LOG.log(Level.FINE, "Executing task: " + task.getName());

        taskListeners.forEach(it -> it.onReady(task));

        boolean flag = false;

        try {
            boolean doDependentsSucceeded = executeTasks(task.getDependents());
            if (!doDependentsSucceeded && task.isRelyingOnDependents() || canceled)
                throw new SilentException();

            if (doDependentsSucceeded)
                task.setDependentsSucceeded();

            task.setVariables(variables);

            task.setState(Task.TaskState.RUNNING);

            taskListeners.forEach(it -> it.onRunning(task));
            try {
                task.getScheduler().schedule(task::execute).get();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof Exception)
                    throw (Exception) e.getCause();
                else
                    throw e;
            } finally {
                task.setState(Task.TaskState.EXECUTED);
            }

            if (task instanceof TaskResult<?>) {
                TaskResult<?> taskResult = (TaskResult<?>) task;
                variables.set(taskResult.getId(), taskResult.getResult());
            }

            if (!executeTasks(task.getDependencies()) && task.isRelyingOnDependencies()) {
                Logging.LOG.severe("Subtasks failed for " + task.getName());
                throw new SilentException();
            }

            flag = true;
            if (task.getSignificance().shouldLog()) {
                Logging.LOG.log(Level.FINER, "Task finished: " + task.getName());
            }

            task.onDone().fireEvent(new TaskEvent(this, task, false));
            taskListeners.forEach(it -> it.onFinished(task));
        } catch (InterruptedException e) {
            if (task.getSignificance().shouldLog()) {
                Logging.LOG.log(Level.FINE, "Task aborted: " + task.getName());
            }
            task.onDone().fireEvent(new TaskEvent(this, task, true));
            taskListeners.forEach(it -> it.onFailed(task, e));
        } catch (SilentException | RejectedExecutionException e) {
            // do nothing
        } catch (Exception e) {
            lastException = e;
            variables.set(LAST_EXCEPTION_ID, e);
            if (task.getSignificance().shouldLog()) {
                Logging.LOG.log(Level.FINE, "Task failed: " + task.getName(), e);
            }
            task.onDone().fireEvent(new TaskEvent(this, task, true));
            taskListeners.forEach(it -> it.onFailed(task, e));
        } finally {
            task.setVariables(null);
        }
        task.setState(flag ? Task.TaskState.SUCCEEDED : Task.TaskState.FAILED);
        return flag;
    }

    public int getRunningTasks() {
        return totTask.get();
    }

    public AutoTypingMap<String> getVariables() {
        return variables;
    }

    private class Invoker implements ExceptionalRunnable<Exception> {

        private final Task task;
        private final CountDownLatch latch;
        private final AtomicBoolean success;

        public Invoker(Task task, CountDownLatch latch, AtomicBoolean success) {
            this.task = task;
            this.latch = latch;
            this.success = success;
        }

        @Override
        public void run() {
            try {
                if (Thread.currentThread().getName().contains("pool"))
                    Thread.currentThread().setName(task.getName());
                if (!executeTask(task))
                    success.set(false);
            } finally {
                latch.countDown();
            }
        }

    }

    public static final String LAST_EXCEPTION_ID = "lastException";
}
