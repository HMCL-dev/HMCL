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
package org.jackhuang.hellominecraft.util.system;

import org.jackhuang.hellominecraft.util.func.Consumer;

/**
 *
 * @author huangyuhui
 */
public class ThreadExecutor extends Thread {

    public final Consumer<Throwable> c;
    public final Runnable r;

    public ThreadExecutor(Consumer<Throwable> c, Runnable r) {
        super();
        this.c = c;
        this.r = r;
    }

    @Override
    public void run() {
        try {
            r.run();
            c.accept(null);
        } catch (Throwable t) {
            c.accept(t);
        }
    }

}
