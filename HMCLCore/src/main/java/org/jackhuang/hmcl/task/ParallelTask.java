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

import java.util.Arrays;
import java.util.Collection;

/**
 * The tasks that provides a way to execute tasks parallelly.
 * Fails when some of {@link #tasks} failed.
 *
 * @author huangyuhui
 */
public final class ParallelTask extends Task {

    private final Collection<Task> tasks;

    /**
     * Constructor.
     *
     * @param tasks the tasks that can be executed parallelly.
     */
    public ParallelTask(Task... tasks) {
        this.tasks = Arrays.asList(tasks);
        setSignificance(TaskSignificance.MINOR);
    }

    public ParallelTask(Collection<Task> tasks) {
        this.tasks = tasks;
        setSignificance(TaskSignificance.MINOR);
    }

    @Override
    public void execute() {
    }

    @Override
    public Collection<Task> getDependents() {
        return tasks;
    }

}
