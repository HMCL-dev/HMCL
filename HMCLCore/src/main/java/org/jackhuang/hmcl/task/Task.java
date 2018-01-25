/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.util.*;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Disposable task.
 *
 * @author huangyuhui
 */
public abstract class Task {

    private final EventManager<TaskEvent> onDone = new EventManager<>();

    private TaskSignificance significance = TaskSignificance.MAJOR;

    /**
     * True if not logging when executing this task.
     */
    public final TaskSignificance getSignificance() {
        return significance;
    }

    public void setSignificance(TaskSignificance significance) {
        this.significance = significance;
    }

    /**
     * The scheduler that decides how this task runs.
     */
    public Scheduler getScheduler() {
        return Schedulers.defaultScheduler();
    }

    /**
     * True if requires all {@link #getDependents} finishing successfully.
     * <p>
     * **Note** if this field is set false, you are not supposed to invoke [run]
     *
     * @defaultValue true
     */
    public boolean isRelyingOnDependents() {
        return true;
    }

    /**
     * True if requires all {@link #getDependencies} finishing successfully.
     * <p>
     * **Note** if this field is set false, you are not supposed to invoke [run]
     *
     * @defaultValue false
     */
    public boolean isRelyingOnDependencies() {
        return true;
    }

    private String name = getClass().toString();

    public String getName() {
        return name;
    }

    public Task setName(String name) {
        this.name = name;
        return this;
    }

    private AutoTypingMap<String> variables = null;

    public AutoTypingMap<String> getVariables() {
        return variables;
    }

    void setVariables(AutoTypingMap<String> variables) {
        this.variables = variables;
    }

    /**
     * @throws InterruptedException if current thread is interrupted
     * @see Thread#isInterrupted
     */
    public abstract void execute() throws Exception;

    /**
     * The collection of sub-tasks that should execute **before** this task running.
     */
    public Collection<Task> getDependents() {
        return Collections.emptySet();
    }

    /**
     * The collection of sub-tasks that should execute **after** this task running.
     */
    public Collection<Task> getDependencies() {
        return Collections.emptySet();
    }

    public EventManager<TaskEvent> onDone() {
        return onDone;
    }

    protected long getProgressInterval() {
        return 1000L;
    }

    private long lastTime = Long.MIN_VALUE;
    private final AtomicReference<Double> progressUpdate = new AtomicReference<>();
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(this, "progress", -1);

    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    protected void updateProgress(int progress, int total) {
        updateProgress(1.0 * progress / total);
    }

    protected void updateProgress(double progress) {
        if (progress < 0 || progress > 1.0)
            throw new IllegalArgumentException("Progress is must between 0 and 1.");
        long now = System.currentTimeMillis();
        if (lastTime == Long.MIN_VALUE || now - lastTime >= getProgressInterval()) {
            updateProgressImmediately(progress);
            lastTime = now;
        }
    }

    protected void updateProgressImmediately(double progress) {
        Properties.updateAsync(this.progress, progress, progressUpdate);
    }

    private final AtomicReference<String> messageUpdate = new AtomicReference<>();
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", null);

    public final ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    protected final void updateMessage(String newMessage) {
        Properties.updateAsync(message, newMessage, messageUpdate);
    }

    public final void run() throws Exception {
        for (Task task : getDependents())
            doSubTask(task);
        execute();
        for (Task task : getDependencies())
            doSubTask(task);
        onDone.fireEvent(new TaskEvent(this, this, false));
    }

    private void doSubTask(Task task) throws Exception {
        message.bind(task.message);
        progress.bind(task.progress);
        task.run();
        message.unbind();
        progress.unbind();
    }

    public final TaskExecutor executor() {
        return new TaskExecutor(this);
    }

    public final TaskExecutor executor(Function<TaskExecutor, TaskListener> taskListener) {
        TaskExecutor executor = new TaskExecutor(this);
        executor.addTaskListener(taskListener.apply(executor));
        return executor;
    }

    public final void start() {
        executor().start();
    }

    public final boolean test() {
        return executor().test();
    }

    public final TaskExecutor subscribe(Task subscriber) {
        TaskExecutor executor = new TaskExecutor(with(subscriber));
        executor.start();
        return executor;
    }

    public final TaskExecutor subscribe(Scheduler scheduler, ExceptionalConsumer<AutoTypingMap<String>, ?> closure) {
        return subscribe(of(scheduler, closure));
    }

    public final TaskExecutor subscribe(Scheduler scheduler, ExceptionalRunnable<?> closure) {
        return subscribe(of(scheduler, i -> closure.run()));
    }

    public final TaskExecutor subscribe(ExceptionalConsumer<AutoTypingMap<String>, ?> closure) {
        return subscribe(of(closure));
    }

    public final TaskExecutor subscribe(ExceptionalRunnable<?> closure) {
        return subscribe(of(closure));
    }

    public final Task then(Task b) {
        return then(s -> b);
    }

    public final Task then(ExceptionalFunction<AutoTypingMap<String>, Task, ?> b) {
        return new CoupleTask<>(this, b, true);
    }

    public final Task with(Task b) {
        return with(s -> b);
    }

    public final Task with(ExceptionalFunction<AutoTypingMap<String>, Task, ?> b) {
        return new CoupleTask<>(this, b, false);
    }

    public static Task empty() {
        return of(s -> {
        });
    }

    public static Task of(String name, ExceptionalRunnable<?> runnable) {
        return of(name, s -> runnable.run());
    }

    public static Task of(ExceptionalRunnable<?> runnable) {
        return of(s -> runnable.run());
    }

    public static Task of(String name, ExceptionalConsumer<AutoTypingMap<String>, ?> closure) {
        return of(name, Schedulers.defaultScheduler(), closure);
    }

    public static Task of(ExceptionalConsumer<AutoTypingMap<String>, ?> closure) {
        return of(Schedulers.defaultScheduler(), closure);
    }

    public static Task of(String name, Scheduler scheduler, ExceptionalConsumer<AutoTypingMap<String>, ?> closure) {
        return new SimpleTask(name, closure, scheduler);
    }

    public static Task of(Scheduler scheduler, ExceptionalConsumer<AutoTypingMap<String>, ?> closure) {
        return of(null, scheduler, closure);
    }

    public static Task of(String name, Scheduler scheduler, ExceptionalRunnable<?> closure) {
        return new SimpleTask(name, i -> closure.run(), scheduler);
    }

    public static Task of(Scheduler scheduler, ExceptionalRunnable<?> closure) {
        return of(null, scheduler, closure);
    }

    public static <V> TaskResult<V> ofResult(String id, Callable<V> callable) {
        return new TaskCallable<>(id, callable);
    }

    public static <V> TaskResult<V> ofResult(String id, Function<AutoTypingMap<String>, V> closure) {
        return new TaskCallable2<>(id, closure);
    }

    public enum TaskSignificance {
        MAJOR,
        MODERATE,
        MINOR;

        public boolean shouldLog() {
            return this != MINOR;
        }

        public boolean shouldShow() {
            return this == MAJOR;
        }
    }
}
