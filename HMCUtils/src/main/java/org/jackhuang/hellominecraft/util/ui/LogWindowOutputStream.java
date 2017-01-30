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
package org.jackhuang.hellominecraft.util.ui;

import java.io.OutputStream;
import java.util.Objects;
import javax.swing.SwingUtilities;
import org.jackhuang.hellominecraft.util.code.Charsets;
import org.jackhuang.hellominecraft.util.log.Level;

/**
 *
 * @author huangyuhui
 */
public class LogWindowOutputStream extends OutputStream {

    private final LogWindow txt;
    private final Level sas;

    public LogWindowOutputStream(LogWindow logWindow, Level l) {
        txt = Objects.requireNonNull(logWindow);
        sas = Objects.requireNonNull(l);
    }

    @Override
    public final void write(byte[] arr) {
        write(arr, 0, arr.length);
    }

    @Override
    public final void write(byte[] arr, int off, int len) {
        append(new String(arr, off, len, Charsets.UTF_8));
    }

    private void append(final String str) {
        SwingUtilities.invokeLater(() -> {
            txt.log(str, Level.guessLevel(str, sas));
        });
    }

    @Override
    public final void write(int i) {
        append(new String(new byte[] { (byte) i }, Charsets.UTF_8));
    }
}
