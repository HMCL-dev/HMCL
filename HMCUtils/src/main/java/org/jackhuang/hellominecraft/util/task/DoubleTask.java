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

import java.util.Arrays;
import java.util.Collection;

/**
 *
 * @author huangyuhui
 */
public class DoubleTask extends TaskInfo {

    Task a, b;

    public DoubleTask(Task a, Task b) {
        this(a, b, "Double Task");
    }

    public DoubleTask(Task a, Task b, String info) {
        super(info);
        this.a = a;
        this.b = b;
        hidden = true;
    }

    @Override
    public Collection<Task> getDependTasks() {
        return Arrays.asList(a);
    }

    @Override
    public Collection<Task> getAfterTasks() {
        return Arrays.asList(b);
    }

    @Override
    public void executeTask(boolean areDependTasksSucceeded) throws Throwable {
        if (!areDependTasksSucceeded)
            throw new IllegalStateException("Depend tasks failed.");
    }

}
