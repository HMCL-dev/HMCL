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

import java.util.Collection;
import java.util.Collections;

/**
 * A task that combines two tasks and make sure [pred] runs before succ.
 *
 * @author huangyuhui
 */
final class FinalizedTask extends Task {

    private final Task pred;
    private final FinalizedCallback callback;
    private final Scheduler scheduler;

    /**
     * A task that combines two tasks and make sure pred runs before succ.
     *
     * @param pred the task that runs before succ.
     * @param callback a callback that returns the task runs after pred, succ will be executed asynchronously. You can do something that relies on the result of pred.
     */
    public FinalizedTask(Task pred, Scheduler scheduler, FinalizedCallback callback) {
        this.pred = pred;
        this.scheduler = scheduler;
        this.callback = callback;

        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public Scheduler getScheduler() {
        return scheduler;
    }

    @Override
    public void execute() throws Exception {
        callback.execute(isDependentsSucceeded(), pred.getLastException());

        if (!isDependentsSucceeded())
            throw new SilentException();
    }

    @Override
    public Collection<Task> getDependents() {
        return Collections.singleton(pred);
    }

    @Override
    public boolean isRelyingOnDependents() {
        return false;
    }
}

