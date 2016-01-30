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
package org.jackhuang.hellominecraft.util;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingWorker;
import org.jackhuang.hellominecraft.util.func.Consumer;

/**
 *
 * @author huangyuhui
 */
public abstract class OverridableSwingWorker<T> extends SwingWorker<Void, T> {

    List<Consumer<T>> processListeners = new ArrayList<>();
    List<Runnable> doneListeners = new ArrayList<>();

    protected abstract void work() throws Exception;

    @Override
    protected void done() {
        for (Runnable c : doneListeners)
            c.run();
    }

    @Override
    protected Void doInBackground() throws Exception {
        work();
        return null;
    }

    public OverridableSwingWorker reg(Consumer<T> c) {
        Utils.requireNonNull(c);
        processListeners.add(c);
        return this;
    }

    public OverridableSwingWorker regDone(Runnable c) {
        Utils.requireNonNull(c);
        doneListeners.add(c);
        return this;
    }

    @Override
    protected void process(List<T> chunks) {
        for (T t : chunks)
            for (Consumer<T> c : processListeners)
                c.accept(t);
    }

}
