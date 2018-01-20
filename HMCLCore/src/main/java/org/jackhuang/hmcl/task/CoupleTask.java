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

import org.jackhuang.hmcl.util.AutoTypingMap;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

/**
 * A task that combines two tasks and make sure [pred] runs before succ.
 *
 * @author huangyuhui
 */
final class CoupleTask<P extends Task> extends Task {

    private final boolean relyingOnDependents;
    private final Collection<Task> dependents;
    private final List<Task> dependencies = new LinkedList<>();
    private final Function<AutoTypingMap<String>, Task> succ;

    /**
     * A task that combines two tasks and make sure pred runs before succ.
     *
     * @param pred the task that runs before succ.
     * @param succ a callback that returns the task runs after pred, succ will be executed asynchronously. You can do something that relies on the result of pred.
     * @param relyingOnDependents true if this task chain will be broken when task pred fails.
     */
    public CoupleTask(P pred, Function<AutoTypingMap<String>, Task> succ, boolean relyingOnDependents) {
        this.dependents = Collections.singleton(pred);
        this.succ = succ;
        this.relyingOnDependents = relyingOnDependents;
    }

    @Override
    public void execute() {
        Task task = succ.apply(getVariables());
        if (task != null)
            dependencies.add(task);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public Collection<Task> getDependents() {
        return dependents;
    }

    @Override
    public Collection<Task> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isRelyingOnDependents() {
        return relyingOnDependents;
    }
}
