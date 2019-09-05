/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 *
 * @author huangyuhui
 */
public final class TaskExecutor {

    private final Task<?> firstTask;
    private final List<TaskListener> taskListeners = new LinkedList<>();
    private Exception exception;
    private final AtomicInteger totTask = new AtomicInteger(0);
    private CompletableFuture<Boolean> future;

    public TaskExecutor(Task<?> task) {
        this.firstTask = task;
    }

    public void addTaskListener(TaskListener taskListener) {
        taskListeners.add(taskListener);
    }

    public Exception getException() {
        return exception;
    }

    public TaskExecutor start() {
        taskListeners.forEach(TaskListener::onStart);
        future = executeTasks(Collections.singleton(firstTask))
                .thenApplyAsync(exception -> {
                    boolean success = exception == null;

                    if (!success) {
                        // We log exception stacktrace because some of exceptions occurred because of bugs.
                        Logging.LOG.log(Level.WARNING, "An exception occurred in task execution", exception);

                        Throwable resolvedException = resolveException(exception);
                        if (resolvedException instanceof RuntimeException &&
                                !(resolvedException instanceof CancellationException) &&
                                !(resolvedException instanceof JsonParseException)) {
                            // Track uncaught RuntimeException which are thrown mostly by our mistake
                            if (uncaughtExceptionHandler != null)
                                uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), resolvedException);
                        }
                    }

                    taskListeners.forEach(it -> it.onStop(success, this));
                    return success;
                })
                .exceptionally(e -> {
                    Lang.handleUncaughtException(resolveException(e));
                    return false;
                });
        return this;
    }

    public boolean test() {
        start();
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignore) {
            // We have dealt with ExecutionException in exception handling and uncaught exception handler.
        } catch (CancellationException e) {
            Logging.LOG.log(Level.INFO, "Task " + firstTask + " has been cancelled.", e);
        }
        return false;
    }

    /**
     * Cancel the subscription ant interrupt all tasks.
     */
    public synchronized void cancel() {
        if (future == null) {
            throw new IllegalStateException("Cannot cancel a not started TaskExecutor");
        }

        future.cancel(true);
    }

    private CompletableFuture<Exception> executeTasks(Collection<Task<?>> tasks) {
        if (tasks == null || tasks.isEmpty())
            return CompletableFuture.completedFuture(null);

        return CompletableFuture.completedFuture(null)
                .thenComposeAsync(unused -> {
                    totTask.addAndGet(tasks.size());

                    return CompletableFuture.allOf(tasks.stream()
                            .map(task -> CompletableFuture.completedFuture(null)
                                    .thenComposeAsync(unused2 -> executeTask(task))
                            ).toArray(CompletableFuture<?>[]::new));
                })
                .thenApplyAsync(unused -> (Exception) null)
                .exceptionally(throwable -> {
                    Throwable resolved = resolveException(throwable);
                    if (resolved instanceof Exception) {
                        return (Exception) resolved;
                    } else {
                        // If an error occurred, we just rethrow it.
                        throw new CompletionException(throwable);
                    }
                });
    }

    private CompletableFuture<?> executeTask(Task<?> task) {
        return CompletableFuture.completedFuture(null)
                .thenComposeAsync(unused -> {
                    task.setState(Task.TaskState.READY);

                    if (task.getSignificance().shouldLog())
                        Logging.LOG.log(Level.FINE, "Executing task: " + task.getName());

                    taskListeners.forEach(it -> it.onReady(task));

                    if (task.doPreExecute()) {
                        return CompletableFuture.runAsync(wrap(task::preExecute), task.getExecutor());
                    } else {
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .thenComposeAsync(unused -> executeTasks(task.getDependents()))
                .thenComposeAsync(dependentsException -> {
                    boolean isDependentsSucceeded = dependentsException == null;

                    if (!isDependentsSucceeded && task.isRelyingOnDependents()) {
                        task.setException(dependentsException);
                        rethrow(dependentsException);
                    }

                    if (isDependentsSucceeded)
                        task.setDependentsSucceeded();

                    return CompletableFuture.runAsync(wrap(() -> {
                        task.setState(Task.TaskState.RUNNING);
                        taskListeners.forEach(it -> it.onRunning(task));
                        task.execute();
                    }), task.getExecutor()).whenComplete((unused, throwable) -> {
                        task.setState(Task.TaskState.EXECUTED);
                        rethrow(throwable);
                    });
                })
                .thenComposeAsync(unused -> executeTasks(task.getDependencies()))
                .thenComposeAsync(dependenciesException -> {
                    boolean isDependenciesSucceeded = dependenciesException == null;

                    if (isDependenciesSucceeded)
                        task.setDependenciesSucceeded();

                    if (task.doPostExecute()) {
                        return CompletableFuture.runAsync(wrap(task::postExecute), task.getExecutor())
                                .thenApply(unused -> dependenciesException);
                    } else {
                        return CompletableFuture.completedFuture(dependenciesException);
                    }
                })
                .thenAcceptAsync(dependenciesException -> {
                    boolean isDependenciesSucceeded = dependenciesException == null;

                    if (!isDependenciesSucceeded && task.isRelyingOnDependencies()) {
                        Logging.LOG.severe("Subtasks failed for " + task.getName());
                        task.setException(dependenciesException);
                        rethrow(dependenciesException);
                    }

                    if (task.getSignificance().shouldLog()) {
                        Logging.LOG.log(Level.FINER, "Task finished: " + task.getName());
                    }

                    task.onDone().fireEvent(new TaskEvent(this, task, false));
                    taskListeners.forEach(it -> it.onFinished(task));

                    task.setState(Task.TaskState.SUCCEEDED);
                })
                .exceptionally(throwable -> {
                    Throwable resolved = resolveException(throwable);
                    if (resolved instanceof Exception) {
                        Exception e = (Exception) resolved;
                        if (e instanceof InterruptedException) {
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

                        task.setState(Task.TaskState.FAILED);
                    }

                    throw new CompletionException(resolved); // rethrow error
                });
    }

    public int getRunningTasks() {
        return totTask.get();
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

    private static Thread.UncaughtExceptionHandler uncaughtExceptionHandler = null;

    public static void setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        TaskExecutor.uncaughtExceptionHandler = uncaughtExceptionHandler;
    }
}
