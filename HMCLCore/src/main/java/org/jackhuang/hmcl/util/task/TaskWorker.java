/*
 * Hello Minecraft! Launcher.
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
package org.jackhuang.hmcl.util.task;

import java.util.List;
import org.jackhuang.hmcl.api.func.Consumer;
import org.jackhuang.hmcl.util.AbstractSwingWorker;

/**
 *
 * @author huang
 */
public abstract class TaskWorker<T> extends Task {

    protected final AbstractSwingWorker<T> worker;

    public TaskWorker() {
        worker = new AbstractSwingWorker<T>() {
            @Override
            protected void work() throws Exception {
                runWithException();
            }
        };
    }

    public TaskWorker<T> reg(Consumer<T> c) {
        worker.reg(c);
        return this;
    }

    public TaskWorker<T> regDone(Runnable c) {
        worker.regDone(c);
        return this;
    }

    public void send(T... result) {
        worker.send(result);
    }

    @Override
    public String getInfo() {
        return "TaskWorker";
    }

    @Override
    public void runAsync() {
        worker.execute();
    }
    
    public List<T> justDo() throws Exception {
        return worker.justDo();
    }
}
