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
package org.jackhuang.hellominecraft.utils;

import org.jackhuang.hellominecraft.utils.functions.Consumer;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

/**
 *
 * @author huangyuhui
 */
public class LauncherPrintStream extends PrintStream {

    private final ArrayList<Consumer<String>> printListeners = new ArrayList<>();

    public LauncherPrintStream(OutputStream paramOutputStream) {
        super(paramOutputStream);
    }

    @Override
    public final void println(String paramString) {
        super.println(paramString);

        for (Consumer<String> a1 : printListeners)
            a1.accept(paramString);
    }

    public final LauncherPrintStream addPrintListener(Consumer<String> paraml) {
        this.printListeners.add(paraml);
        return this;
    }
}
