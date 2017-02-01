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

import java.util.Collection;
import java.util.HashSet;

/**
 *
 * @author huangyuhui
 */
public class ParallelTask extends Task {

    Collection<Task> tasks = new HashSet<>();

    public ParallelTask() {
        hidden = true;
    }

    @Override
    public void executeTask(boolean areDependTasksSucceeded) {
    }

    @Override
    public String getInfo() {
        return "PARALLEL";
    }

    @Override
    public Collection<Task> getDependTasks() {
        return tasks;
    }

    public void addTask(Task t) {
        tasks.add(t);
    }

}
