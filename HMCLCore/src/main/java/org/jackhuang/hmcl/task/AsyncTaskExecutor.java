/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Lang.*;

/**
 *
 * @author huangyuhui
 */
public final class AsyncTaskExecutor extends TaskExecutor {

    private CompletableFuture<Boolean> future;

    public AsyncTaskExecutor(Task<?> task) {
        super(task);
    }

    @Override
    public TaskExecutor start() {
        taskListeners.forEach(TaskListener::onStart);
        future = executeTasks(null, Collections.singleton(firstTask))
                .thenApplyAsync(exception -> {
                    boolean success = exception == null;

                    if (!success) {
                        // We log exception stacktrace because some of exceptions occurred because of bugs.
                        Logging.LOG.log(Level.WARNING, "An exception occurred in task execution", exception);

                        Throwable resolvedException = resolveException(exception);
                        if (resolvedException instanceof RuntimeException &&
                                !(resolvedException instanceof CancellationException) &&
                                !(resolvedException instanceof JsonParseException) &&
                                !(resolvedException instanceof RejectedExecutionException)) {
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

    @Override
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

    @Override
    public synchronized void cancel() {
        if (future == null) {
            throw new IllegalStateException("Cannot cancel a not started TaskExecutor");
        }

        cancelled.set(true);
    }

    private CompletableFuture<?> executeTasksExceptionally(Task<?> parentTask, Collection<? extends Task<?>> tasks) {
        if (tasks == null || tasks.isEmpty())
            return CompletableFuture.completedFuture(null);

        return CompletableFuture.completedFuture(null)
                .thenComposeAsync(unused -> {
                    totTask.addAndGet(tasks.size());

                    if (isCancelled()) {
                        for (Task<?> task : tasks) task.setException(new CancellationException());
                        return CompletableFuture.runAsync(this::checkCancellation);
                    }

                    return CompletableFuture.allOf(tasks.stream()
                            .map(task -> CompletableFuture.completedFuture(null)
                                    .thenComposeAsync(unused2 -> executeTask(parentTask, task))
                            ).toArray(CompletableFuture<?>[]::new));
                });
    }

    private CompletableFuture<Exception> executeTasks(Task<?> parentTask, Collection<? extends Task<?>> tasks) {
        return executeTasksExceptionally(parentTask, tasks)
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

    private <T> CompletableFuture<T> executeCompletableFutureTask(Task<?> parentTask, CompletableFutureTask<T> task) {
        return CompletableFuture.completedFuture(null)
                .thenComposeAsync(unused -> {
                    checkCancellation();

                    task.setCancelled(this::isCancelled);
                    task.setState(Task.TaskState.READY);
                    if (parentTask != null && task.getStage() == null)
                        task.setStage(parentTask.getStage());

                    if (task.getSignificance().shouldLog())
                        Logging.LOG.log(Level.FINE, "Executing task: " + task.getName());

                    taskListeners.forEach(it -> it.onReady(task));

                    return task.getFuture(new TaskCompletableFuture() {
                        @Override
                        public <T2> CompletableFuture<T2> one(Task<T2> subtask) {
                            return executeTask(task, subtask);
                        }

                        @Override
                        public CompletableFuture<?> all(Collection<Task<?>> tasks) {
                            return executeTasksExceptionally(task, tasks);
                        }
                    });
                })
                .thenApplyAsync(result -> {
                    checkCancellation();

                    if (task.getSignificance().shouldLog()) {
                        Logging.LOG.log(Level.FINER, "Task finished: " + task.getName());
                    }

                    task.setResult(result);
                    task.onDone().fireEvent(new TaskEvent(this, task, false));
                    taskListeners.forEach(it -> it.onFinished(task));

                    task.setState(Task.TaskState.SUCCEEDED);

                    return result;
                })
                .exceptionally(throwable -> {
                    Throwable resolved = resolveException(throwable);
                    if (resolved instanceof Exception) {
                        Exception e = (Exception) resolved;
                        if (e instanceof InterruptedException || e instanceof CancellationException) {
                            task.setException(null);
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

    private <T> CompletableFuture<T> executeNormalTask(Task<?> parentTask, Task<T> task) {
        return CompletableFuture.completedFuture(null)
                .thenComposeAsync(unused -> {
                    checkCancellation();

                    task.setCancelled(this::isCancelled);
                    task.setState(Task.TaskState.READY);
                    if (task.getStage() != null) {
                        task.setInheritedStage(task.getStage());
                    } else if (parentTask != null) {
                        task.setInheritedStage(parentTask.getInheritedStage());
                    }
                    task.setNotifyPropertiesChanged(() -> taskListeners.forEach(it -> it.onPropertiesUpdate(task)));

                    if (task.getSignificance().shouldLog())
                        Logging.LOG.log(Level.FINE, "Executing task: " + task.getName());

                    taskListeners.forEach(it -> it.onReady(task));

                    if (task.doPreExecute()) {
                        return CompletableFuture.runAsync(wrap(task::preExecute), task.getExecutor());
                    } else {
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .thenComposeAsync(unused -> executeTasks(task, task.getDependents()))
                .thenComposeAsync(dependentsException -> {
                    boolean isDependentsSucceeded = dependentsException == null;

                    if (isDependentsSucceeded) {
                        task.setDependentsSucceeded();
                    } else {
                        task.setException(dependentsException);

                        if (task.isRelyingOnDependents()) {
                            rethrow(dependentsException);
                        }
                    }

                    return CompletableFuture.runAsync(wrap(() -> {
                        task.setState(Task.TaskState.RUNNING);
                        taskListeners.forEach(it -> it.onRunning(task));
                        task.execute();
                    }), task.getExecutor()).whenComplete((unused, throwable) -> {
                        task.setState(Task.TaskState.EXECUTED);
                        rethrow(throwable);
                    });
                })
                .thenComposeAsync(unused -> executeTasks(task, task.getDependencies()))
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
                .thenApplyAsync(dependenciesException -> {
                    boolean isDependenciesSucceeded = dependenciesException == null;

                    if (!isDependenciesSucceeded) {
                        Logging.LOG.severe("Subtasks failed for " + task.getName());
                        task.setException(dependenciesException);
                        if (task.isRelyingOnDependencies()) {
                            rethrow(dependenciesException);
                        }
                    }

                    checkCancellation();

                    if (task.getSignificance().shouldLog()) {
                        Logging.LOG.log(Level.FINER, "Task finished: " + task.getName());
                    }

                    task.onDone().fireEvent(new TaskEvent(this, task, false));
                    taskListeners.forEach(it -> it.onFinished(task));

                    task.setState(Task.TaskState.SUCCEEDED);

                    return task.getResult();
                })
                .exceptionally(throwable -> {
                    Throwable resolved = resolveException(throwable);
                    if (resolved instanceof Exception) {
                        Exception e = convertInterruptedException((Exception) resolved);
                        task.setException(e);
                        exception = e;
                        if (e instanceof CancellationException) {
                            if (task.getSignificance().shouldLog()) {
                                Logging.LOG.log(Level.FINE, "Task aborted: " + task.getName());
                            }
                        } else {
                            if (task.getSignificance().shouldLog()) {
                                Logging.LOG.log(Level.FINE, "Task failed: " + task.getName(), e);
                            }
                        }
                        task.onDone().fireEvent(new TaskEvent(this, task, true));
                        taskListeners.forEach(it -> it.onFailed(task, e));

                        task.setState(Task.TaskState.FAILED);
                    }

                    throw new CompletionException(resolved); // rethrow error
                });
    }

    private <T> CompletableFuture<T> executeTask(Task<?> parentTask, Task<T> task) {
        if (task instanceof CompletableFutureTask<?>) {
            return executeCompletableFutureTask(parentTask, (CompletableFutureTask<T>) task);
        } else {
            return executeNormalTask(parentTask, task);
        }
    }

    private void checkCancellation() {
        if (isCancelled()) {
            throw new CancellationException("Cancelled by user");
        }
    }

    private static Exception convertInterruptedException(Exception e) {
        if (e instanceof InterruptedException) {
            return new CancellationException(e.getMessage());
        } else {
            return e;
        }
    }

    private static Thread.UncaughtExceptionHandler uncaughtExceptionHandler = null;

    public static void setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        AsyncTaskExecutor.uncaughtExceptionHandler = uncaughtExceptionHandler;
    }
}
