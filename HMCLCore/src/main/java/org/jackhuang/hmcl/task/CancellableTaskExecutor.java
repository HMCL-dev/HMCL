/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

public class CancellableTaskExecutor extends TaskExecutor {

    private final ConcurrentLinkedQueue<Future<?>> workerQueue = new ConcurrentLinkedQueue<>();
    private Executor scheduler = Schedulers.newThread();

    public CancellableTaskExecutor(Task<?> task) {
        super(task);
    }

    @Override
    public TaskExecutor start() {
        taskListeners.forEach(TaskListener::onStart);
        workerQueue.add(Schedulers.schedule(scheduler, wrap(() -> {
            boolean flag = executeTasks(null, Collections.singleton(firstTask));
            taskListeners.forEach(it -> it.onStop(flag, this));
        })));
        return this;
    }

    @Override
    public boolean test() {
        taskListeners.forEach(TaskListener::onStart);
        AtomicBoolean flag = new AtomicBoolean(true);
        Future<?> future = Schedulers.schedule(scheduler, wrap(() -> {
            flag.set(executeTasks(null, Collections.singleton(firstTask)));
            taskListeners.forEach(it -> it.onStop(flag.get(), this));
        }));
        workerQueue.add(future);
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | CancellationException ignored) {
        }
        return flag.get();
    }

    @Override
    public synchronized void cancel() {
        cancelled.set(true);

        while (!workerQueue.isEmpty()) {
            Future<?> future = workerQueue.poll();
            if (future != null)
                future.cancel(true);
        }
    }

    private boolean executeTasks(Task<?> parentTask, Collection<? extends Task<?>> tasks) throws InterruptedException {
        if (tasks.isEmpty())
            return true;

        totTask.addAndGet(tasks.size());
        AtomicBoolean success = new AtomicBoolean(true);
        CountDownLatch latch = new CountDownLatch(tasks.size());
        for (Task<?> task : tasks) {
            if (cancelled.get())
                return false;
            Invoker invoker = new Invoker(parentTask, task, latch, success);
            try {
                Future<?> future = Schedulers.schedule(scheduler, invoker);
                workerQueue.add(future);
            } catch (RejectedExecutionException e) {
                throw new InterruptedException();
            }
        }

        if (cancelled.get())
            return false;

        try {
            latch.await();
        } catch (InterruptedException e) {
            return false;
        }
        return success.get() && !cancelled.get();
    }

    private synchronized void updateStageProperties(String stage, Map<String, Object> taskProperties) {
        stageProperties.putIfAbsent(stage, new HashMap<>());
        Map<String, Object> prop = stageProperties.get(stage);
        for (Map.Entry<String, Object> entry : taskProperties.entrySet()) {
            if (entry.getValue() instanceof UnaryOperator) {
                prop.put(entry.getKey(), ((UnaryOperator) entry.getValue()).apply(prop.get(entry.getKey())));
            } else {
                prop.put(entry.getKey(), entry.getValue());
            }
        }
        taskListeners.forEach(taskListener -> taskListener.onPropertiesUpdate(stageProperties));
    }

    private boolean executeTask(Task<?> parentTask, Task<?> task) {
        task.setCancelled(this::isCancelled);

        if (cancelled.get()) {
            task.setState(Task.TaskState.FAILED);
            task.setException(new CancellationException());
            return false;
        }

        task.setState(Task.TaskState.READY);
        if (parentTask != null && task.getStage() == null)
            task.setStage(parentTask.getStage());

        if (task.getSignificance().shouldLog())
            Logging.LOG.log(Level.FINE, "Executing task: " + task.getName());

        taskListeners.forEach(it -> it.onReady(task));

        boolean flag = false;

        try {
            if (task.doPreExecute()) {
                try {
                    Schedulers.schedule(task.getExecutor(), wrap(task::preExecute)).get();
                } catch (ExecutionException e) {
                    rethrow(e);
                }
            }

            Collection<? extends Task<?>> dependents = task.getDependents();
            boolean doDependentsSucceeded = executeTasks(task, dependents);
            Exception dependentsException = dependents.stream().map(Task::getException)
                    .filter(Objects::nonNull)
                    .filter(x -> !(x instanceof CancellationException))
                    .filter(x -> !(x instanceof InterruptedException))
                    .findAny().orElse(null);
            if (!doDependentsSucceeded && task.isRelyingOnDependents() || cancelled.get()) {
                task.setException(dependentsException);
                throw new ExecutionException(dependentsException);
            }

            if (doDependentsSucceeded)
                task.setDependentsSucceeded();

            try {
                Schedulers.schedule(task.getExecutor(), wrap(() -> {
                    task.setState(Task.TaskState.RUNNING);
                    taskListeners.forEach(it -> it.onRunning(task));
                    task.execute();
                })).get();
            } catch (ExecutionException e) {
                rethrow(e);
            } finally {
                task.setState(Task.TaskState.EXECUTED);
            }

            if (task.properties != null) {
                updateStageProperties(task.getStage(), task.properties);
            }

            Collection<? extends Task<?>> dependencies = task.getDependencies();
            boolean doDependenciesSucceeded = executeTasks(task, dependencies);
            Exception dependenciesException = dependencies.stream().map(Task::getException)
                    .filter(Objects::nonNull)
                    .filter(x -> !(x instanceof CancellationException))
                    .filter(x -> !(x instanceof InterruptedException))
                    .findAny().orElse(null);

            if (doDependenciesSucceeded)
                task.setDependenciesSucceeded();

            if (task.doPostExecute()) {
                try {
                    Schedulers.schedule(task.getExecutor(), wrap(task::postExecute)).get();
                } catch (ExecutionException e) {
                    rethrow(e);
                }
            }

            if (!doDependenciesSucceeded && task.isRelyingOnDependencies()) {
                Logging.LOG.severe("Subtasks failed for " + task.getName());
                task.setException(dependenciesException);
                throw new ExecutionException(dependenciesException);
            }

            flag = true;
            if (task.getSignificance().shouldLog()) {
                Logging.LOG.log(Level.FINER, "Task finished: " + task.getName());
            }

            if (task.properties != null) {
                updateStageProperties(task.getStage(), task.properties);
            }

            task.onDone().fireEvent(new TaskEvent(this, task, false));
            taskListeners.forEach(it -> it.onFinished(task));
        } catch (RejectedExecutionException e) {
            Logging.LOG.log(Level.SEVERE, "Task rejected: " + task.getName(), e);
        } catch (Exception throwable) {
            Throwable resolved = resolveException(throwable);
            if (resolved instanceof Exception) {
                Exception e = (Exception) resolved;
                if (e instanceof InterruptedException || e instanceof CancellationException) {
                    task.setException(e);
                    if (task.getSignificance().shouldLog()) {
                        Logging.LOG.log(Level.FINE, "Task aborted: " + task.getName());
                    }
                    task.onDone().fireEvent(new TaskEvent(this, task, true));
                    taskListeners.forEach(it -> it.onFailed(task, e));
                } else {
                    task.setException(e);
                    exception = e;
                    if (task.getSignificance().shouldLog()) {
                        Logging.LOG.log(Level.FINE, "Task failed: " + task.getName(), e);
                    }
                    task.onDone().fireEvent(new TaskEvent(this, task, true));
                    taskListeners.forEach(it -> it.onFailed(task, e));
                }
            } else if (resolved instanceof Error) {
                throw (Error) resolved;
            }
        }
        task.setState(flag ? Task.TaskState.SUCCEEDED : Task.TaskState.FAILED);
        return flag;
    }

    private static Throwable resolveException(Throwable e) {
        if (e instanceof ExecutionException || e instanceof CompletionException)
            return resolveException(e.getCause());
        else
            return e;
    }

    private static void rethrow(Throwable e) {
        if (e == null)
            return;
        if (e instanceof ExecutionException || e instanceof CompletionException) { // including UncheckedException and UncheckedThrowable
            rethrow(e.getCause());
        } else if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        } else {
            throw new CompletionException(e);
        }
    }

    private static Runnable wrap(ExceptionalRunnable<?> runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                rethrow(e);
            }
        };
    }

    private class Invoker implements Runnable {

        private final Task<?> parentTask;
        private final Task<?> task;
        private final CountDownLatch latch;
        private final AtomicBoolean success;

        public Invoker(Task<?> parentTask, Task<?> task, CountDownLatch latch, AtomicBoolean success) {
            this.parentTask = parentTask;
            this.task = task;
            this.latch = latch;
            this.success = success;
        }

        @Override
        public void run() {
            try {
                if (task.getName() != null)
                    Thread.currentThread().setName(task.getName());
                if (!executeTask(parentTask, task))
                    success.set(false);
            } finally {
                latch.countDown();
            }
        }

    }
}
