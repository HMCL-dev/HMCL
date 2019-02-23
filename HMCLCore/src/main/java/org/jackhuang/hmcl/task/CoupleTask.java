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

import org.jackhuang.hmcl.util.AutoTypingMap;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;

import java.util.Collection;
import java.util.Collections;

/**
 * A task that combines two tasks and make sure [pred] runs before succ.
 *
 * @author huangyuhui
 */
final class CoupleTask extends Task {

    private final boolean relyingOnDependents;
    private final Task pred;
    private Task succ;
    private final ExceptionalSupplier<Task, ?> supplier;

    /**
     * A task that combines two tasks and make sure pred runs before succ.
     *
     * @param pred the task that runs before supplier.
     * @param supplier a callback that returns the task runs after pred, succ will be executed asynchronously. You can do something that relies on the result of pred.
     * @param relyingOnDependents true if this task chain will be broken when task pred fails.
     */
    CoupleTask(Task pred, ExceptionalSupplier<Task, ?> supplier, boolean relyingOnDependents) {
        this.pred = pred;
        this.supplier = supplier;
        this.relyingOnDependents = relyingOnDependents;

        setSignificance(TaskSignificance.MODERATE);
        setName(supplier.toString());
    }

    @Override
    public void execute() throws Exception {
        setName(supplier.toString());
        succ = supplier.get();
    }

    @Override
    public Collection<Task> getDependents() {
        return pred == null ? Collections.emptySet() : Collections.singleton(pred);
    }

    @Override
    public Collection<Task> getDependencies() {
        return succ == null ? Collections.emptySet() : Collections.singleton(succ);
    }

    @Override
    public boolean isRelyingOnDependents() {
        return relyingOnDependents;
    }
}
