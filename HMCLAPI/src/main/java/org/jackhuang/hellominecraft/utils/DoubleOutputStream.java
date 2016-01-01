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

import java.io.IOException;
import java.io.OutputStream;

/**
 *
 * @author huangyuhui
 */
public class DoubleOutputStream extends OutputStream {

    private OutputStream a = null;
    private OutputStream b = null;
    private boolean c = true;

    public DoubleOutputStream(OutputStream paramOutputStream1, OutputStream paramOutputStream2) {
        this(paramOutputStream1, paramOutputStream2, true);
    }

    private DoubleOutputStream(OutputStream paramOutputStream1, OutputStream paramOutputStream2, boolean paramBoolean) {
        this.a = paramOutputStream1;
        this.b = paramOutputStream2;
        this.c = true;
    }

    @Override
    public final void write(byte[] arr, int off, int len) throws IOException {
        if (this.a != null)
            this.a.write(arr, off, len);
        if (this.b != null)
            this.b.write(arr, off, len);
        if (this.c)
            flush();
    }

    @Override
    public final void write(byte[] paramArrayOfByte) throws IOException {
        if (this.a != null)
            this.a.write(paramArrayOfByte);
        if (this.b != null)
            this.b.write(paramArrayOfByte);
        if (this.c)
            flush();
    }

    @Override
    public final void write(int paramInt) throws IOException {
        if (this.a != null)
            this.a.write(paramInt);
        if (this.b != null)
            this.b.write(paramInt);
        if (this.c)
            flush();
    }

    @Override
    public final void close() throws IOException {
        flush();

        if (this.a != null)
            this.a.close();
        if (this.b != null)
            this.b.close();
    }

    @Override
    public final void flush() throws IOException {
        if (this.a != null)
            this.a.flush();
        if (this.b != null)
            this.b.flush();
    }
}
