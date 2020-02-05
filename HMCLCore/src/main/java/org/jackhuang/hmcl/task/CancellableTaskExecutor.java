package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
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
            boolean flag = executeTasks(Collections.singleton(firstTask));
            taskListeners.forEach(it -> it.onStop(flag, this));
        })));
        return this;
    }

    @Override
    public boolean test() {
        taskListeners.forEach(TaskListener::onStart);
        AtomicBoolean flag = new AtomicBoolean(true);
        Future<?> future = Schedulers.schedule(scheduler, wrap(() -> {
            flag.set(executeTasks(Collections.singleton(firstTask)));
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

    private boolean executeTasks(Collection<? extends Task<?>> tasks) throws InterruptedException {
        if (tasks.isEmpty())
            return true;

        totTask.addAndGet(tasks.size());
        AtomicBoolean success = new AtomicBoolean(true);
        CountDownLatch latch = new CountDownLatch(tasks.size());
        for (Task<?> task : tasks) {
            if (cancelled.get())
                return false;
            Invoker invoker = new Invoker(task, latch, success);
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

    private boolean executeTask(Task<?> task) {
        task.setCancelled(this::isCancelled);

        if (cancelled.get()) {
            task.setState(Task.TaskState.FAILED);
            task.setException(new CancellationException());
            return false;
        }

        task.setState(Task.TaskState.READY);

        if (task.getSignificance().shouldLog())
            Logging.LOG.log(Level.FINE, "Executing task: " + task.getName());

        taskListeners.forEach(it -> it.onReady(task));

        boolean flag = false;

        try {
            if (task.doPreExecute()) {
                try {
                    Schedulers.schedule(task.getExecutor(), wrap(task::preExecute)).get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof Exception)
                        throw (Exception) e.getCause();
                    else
                        throw e;
                }
            }

            Collection<? extends Task<?>> dependents = task.getDependents();
            boolean doDependentsSucceeded = executeTasks(dependents);
            Exception dependentsException = dependents.stream().map(Task::getException).filter(Objects::nonNull).findAny().orElse(null);
            if (!doDependentsSucceeded && task.isRelyingOnDependents() || cancelled.get()) {
                task.setException(dependentsException);
                throw new CancellationException();
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
                if (e.getCause() instanceof Exception)
                    throw (Exception) e.getCause();
                else
                    throw e;
            } finally {
                task.setState(Task.TaskState.EXECUTED);
            }

            Collection<? extends Task<?>> dependencies = task.getDependencies();
            boolean doDependenciesSucceeded = executeTasks(dependencies);
            Exception dependenciesException = dependencies.stream().map(Task::getException).filter(Objects::nonNull).findAny().orElse(null);

            if (doDependenciesSucceeded)
                task.setDependenciesSucceeded();

            if (task.doPostExecute()) {
                try {
                    Schedulers.schedule(task.getExecutor(), wrap(task::postExecute)).get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof Exception)
                        throw (Exception) e.getCause();
                    else
                        throw e;
                }
            }

            if (!doDependenciesSucceeded && task.isRelyingOnDependencies()) {
                Logging.LOG.severe("Subtasks failed for " + task.getName());
                task.setException(dependenciesException);
                throw new CancellationException();
            }

            flag = true;
            if (task.getSignificance().shouldLog()) {
                Logging.LOG.log(Level.FINER, "Task finished: " + task.getName());
            }

            task.onDone().fireEvent(new TaskEvent(this, task, false));
            taskListeners.forEach(it -> it.onFinished(task));
        } catch (InterruptedException e) {
            task.setException(e);
            if (task.getSignificance().shouldLog()) {
                Logging.LOG.log(Level.FINE, "Task aborted: " + task.getName());
            }
            task.onDone().fireEvent(new TaskEvent(this, task, true));
            taskListeners.forEach(it -> it.onFailed(task, e));
        } catch (CancellationException | RejectedExecutionException e) {
            if (task.getException() == null)
                task.setException(e);
        } catch (Exception e) {
            task.setException(e);
            exception = e;
            if (task.getSignificance().shouldLog()) {
                Logging.LOG.log(Level.FINE, "Task failed: " + task.getName(), e);
            }
            task.onDone().fireEvent(new TaskEvent(this, task, true));
            taskListeners.forEach(it -> it.onFailed(task, e));
        }
        task.setState(flag ? Task.TaskState.SUCCEEDED : Task.TaskState.FAILED);
        return flag;
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

        private final Task<?> task;
        private final CountDownLatch latch;
        private final AtomicBoolean success;

        public Invoker(Task<?> task, CountDownLatch latch, AtomicBoolean success) {
            this.task = task;
            this.latch = latch;
            this.success = success;
        }

        @Override
        public void run() {
            try {
                Thread.currentThread().setName(task.getName());
                if (!executeTask(task))
                    success.set(false);
            } finally {
                latch.countDown();
            }
        }

    }
}
