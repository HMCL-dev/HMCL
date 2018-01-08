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

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import org.jackhuang.hmcl.util.AutoTypingMap;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.util.ExceptionalConsumer;
import org.jackhuang.hmcl.util.ExceptionalRunnable;
import org.jackhuang.hmcl.util.Properties;

/**
 * Disposable task.
 *
 * @author huangyuhui
 */
public abstract class Task {

    private final EventManager<TaskEvent> onDone = new EventManager<>();

    /**
     * True if not logging when executing this task.
     */
    public boolean isHidden() {
        return false;
    }

    /**
     * The scheduler that decides how this task runs.
     */
    public Scheduler getScheduler() {
        return Schedulers.defaultScheduler();
    }

    /**
     * True if requires all {@link #getDependents} finishing successfully.
     *
     * **Note** if this field is set false, you are not supposed to invoke [run]
     *
     * @defaultValue true
     */
    public boolean isRelyingOnDependents() {
        return true;
    }

    /**
     * True if requires all {@link #getDependencies} finishing successfully.
     *
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

    public void setName(String name) {
        this.name = name;
    }

    private AutoTypingMap<String> variables = null;

    public AutoTypingMap<String> getVariables() {
        return variables;
    }

    void setVariables(AutoTypingMap<String> variables) {
        this.variables = variables;
    }

    /**
     * @see Thread#isInterrupted
     * @throws InterruptedException if current thread is interrupted
     */
    public abstract void execute() throws Exception;

    /**
     * The collection of sub-tasks that should execute **before** this task running.
     */
    public Collection<Task> getDependents() {
        return Collections.EMPTY_SET;
    }

    /**
     * The collection of sub-tasks that should execute **after** this task running.
     */
    public Collection<Task> getDependencies() {
        return Collections.EMPTY_SET;
    }

    public EventManager<TaskEvent> onDone() {
        return onDone;
    }

    protected long getProgressInterval() {
        return 1000L;
    }

    private long lastTime = Long.MIN_VALUE;
    private final AtomicReference<Double> progressUpdate = new AtomicReference<>();
    private final ReadOnlyDoubleWrapper progressProperty = new ReadOnlyDoubleWrapper(this, "progress", 0);

    public ReadOnlyDoubleProperty getProgressProperty() {
        return progressProperty.getReadOnlyProperty();
    }

    protected void updateProgress(int progress, int total) {
        updateProgress(1.0 * progress / total);
    }

    protected void updateProgress(double progress) {
        if (progress > 1.0)
            throw new IllegalArgumentException("Progress is must between 0 and 1.");
        long now = System.currentTimeMillis();
        if (now - lastTime >= getProgressInterval()) {
            updateProgressImmediately(progress);
            lastTime = now;
        }
    }

    protected void updateProgressImmediately(double progress) {
        Properties.updateAsync(progressProperty, progress, progressUpdate);
    }

    private final AtomicReference<String> messageUpdate = new AtomicReference<>();
    private final ReadOnlyStringWrapper messageProperty = new ReadOnlyStringWrapper(this, "message", null);

    public final ReadOnlyStringProperty getMessageProperty() {
        return messageProperty.getReadOnlyProperty();
    }

    protected final void updateMessage(String newMessage) {
        Properties.updateAsync(messageProperty, newMessage, messageUpdate);
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
        messageProperty.bind(task.messageProperty);
        progressProperty.bind(task.progressProperty);
        task.run();
        messageProperty.unbind();
        progressProperty.unbind();
    }

    ;
    
    public final TaskExecutor executor() {
        return new TaskExecutor(this);
    }

    public final TaskExecutor executor(TaskListener taskListener) {
        TaskExecutor executor = new TaskExecutor(this);
        executor.setTaskListener(taskListener);
        return executor;
    }

    public final void start() {
        executor().start();
    }

    public final boolean test() throws Exception {
        return executor().test();
    }

    public final TaskExecutor subscribe(Task subscriber) {
        TaskExecutor executor = new TaskExecutor(with(subscriber));
        executor.start();
        return executor;
    }

    public final TaskExecutor subscribe(Scheduler scheduler, ExceptionalConsumer<AutoTypingMap<String>, ?> closure) {
        return subscribe(of(closure, scheduler));
    }

    public final TaskExecutor subscribe(ExceptionalConsumer<AutoTypingMap<String>, ?> closure) {
        return subscribe(of(closure));
    }

    public final Task then(Task b) {
        return then(s -> b);
    }

    public final Task then(Function<AutoTypingMap<String>, Task> b) {
        return new CoupleTask<>(this, b, true);
    }

    public final Task with(Task b) {
        return with(s -> b);
    }

    public final Task with(Function<AutoTypingMap<String>, Task> b) {
        return new CoupleTask<>(this, b, false);
    }

    public static Task of(ExceptionalRunnable<?> runnable) {
        return of(s -> runnable.run());
    }

    public static Task of(ExceptionalConsumer<AutoTypingMap<String>, ?> closure) {
        return of(closure, Schedulers.defaultScheduler());
    }

    public static Task of(ExceptionalConsumer<AutoTypingMap<String>, ?> closure, Scheduler scheduler) {
        return new SimpleTask(closure, scheduler);
    }

    public static <V> TaskResult<V> ofResult(String id, Callable<V> callable) {
        return new TaskCallable<>(id, callable);
    }

    public static <V> TaskResult<V> ofResult(String id, Function<AutoTypingMap<String>, V> closure) {
        return new TaskCallable2<>(id, closure);
    }
}
