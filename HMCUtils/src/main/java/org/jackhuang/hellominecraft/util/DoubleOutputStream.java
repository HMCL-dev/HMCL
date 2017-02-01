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
package org.jackhuang.hellominecraft.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 *
 * @author huangyuhui
 */
public class DoubleOutputStream extends OutputStream {

    private OutputStream os1 = null;
    private OutputStream os2 = null;
    private boolean autoFlush = true;

    public DoubleOutputStream(OutputStream os1, OutputStream os2) {
        this(os1, os2, true);
    }

    private DoubleOutputStream(OutputStream os1, OutputStream os2, boolean autoFlush) {
        this.os1 = os1;
        this.os2 = os2;
        this.autoFlush = autoFlush;
    }

    @Override
    public final void write(byte[] arr, int off, int len) throws IOException {
        if (this.os1 != null)
            this.os1.write(arr, off, len);
        if (this.os2 != null)
            this.os2.write(arr, off, len);
        if (this.autoFlush)
            flush();
    }

    @Override
    public final void write(byte[] arr) throws IOException {
        if (this.os1 != null)
            this.os1.write(arr);
        if (this.os2 != null)
            this.os2.write(arr);
        if (this.autoFlush)
            flush();
    }

    @Override
    public final void write(int i) throws IOException {
        if (this.os1 != null)
            this.os1.write(i);
        if (this.os2 != null)
            this.os2.write(i);
        if (this.autoFlush)
            flush();
    }

    @Override
    public final void close() throws IOException {
        flush();

        if (this.os1 != null)
            this.os1.close();
        if (this.os2 != null)
            this.os2.close();
    }

    @Override
    public final void flush() throws IOException {
        if (this.os1 != null)
            this.os1.flush();
        if (this.os2 != null)
            this.os2.flush();
    }
}
