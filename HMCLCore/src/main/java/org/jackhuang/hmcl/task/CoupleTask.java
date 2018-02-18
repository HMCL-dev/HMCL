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
import org.jackhuang.hmcl.util.ExceptionalFunction;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * A task that combines two tasks and make sure [pred] runs before succ.
 *
 * @author huangyuhui
 */
final class CoupleTask<P extends Task> extends Task {

    private final boolean relyingOnDependents;
    private final boolean failIfDependentsFail;
    private final Collection<Task> dependents;
    private final List<Task> dependencies = new LinkedList<>();
    private final ExceptionalFunction<AutoTypingMap<String>, Task, ?> succ;

    /**
     * A task that combines two tasks and make sure pred runs before succ.
     *
     * @param pred the task that runs before succ.
     * @param succ a callback that returns the task runs after pred, succ will be executed asynchronously. You can do something that relies on the result of pred.
     * @param relyingOnDependents true if this task chain will be broken when task pred fails.
     */
    public CoupleTask(P pred, ExceptionalFunction<AutoTypingMap<String>, Task, ?> succ, boolean relyingOnDependents, boolean failIfDependentsFail) {
        this.dependents = Collections.singleton(pred);
        this.succ = succ;
        this.relyingOnDependents = relyingOnDependents;
        this.failIfDependentsFail = failIfDependentsFail;

        setSignificance(TaskSignificance.MODERATE);
        setName(succ.toString());
    }

    @Override
    public void execute() throws Exception {
        setName(succ.toString());
        Task task = succ.apply(getVariables());
        if (task != null)
            dependencies.add(task);

        if (failIfDependentsFail && !isDependentsSucceeded())
            throw new SilentException();
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
