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
package org.jackhuang.hellominecraft.tasks;

import java.util.ArrayList;
import org.jackhuang.hellominecraft.tasks.communication.PreviousResult;
import org.jackhuang.hellominecraft.tasks.communication.PreviousResultRegistrar;
import org.jackhuang.hellominecraft.utils.functions.Consumer;

/**
 *
 * @author huangyuhui
 * @param <T> Runnable&lt;T&gt;
 */
public class TaskRunnableArg1<T> extends TaskInfo implements PreviousResultRegistrar<T> {

    private final Consumer<T> r;

    public TaskRunnableArg1(String info, Consumer<T> r) {
        super(info);
        this.r = r;
    }

    @Override
    public void executeTask() throws Exception {
        if (al.size() != 1)
            throw new IllegalStateException("the count of args is not one.");
        r.accept(al.get(0).getResult());
    }

    ArrayList<PreviousResult<T>> al = new ArrayList();

    @Override
    public Task registerPreviousResult(PreviousResult<T> pr) {
        al.add(pr);
        return this;
    }

}
