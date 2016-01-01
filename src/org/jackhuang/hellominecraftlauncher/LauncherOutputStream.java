/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher;

import java.io.IOException;
import java.io.OutputStream;

/**
 *
 * @author hyh
 */
public final class LauncherOutputStream extends OutputStream {

    private OutputStream a = null;
    private OutputStream b = null;
    private boolean c = true;

    public LauncherOutputStream(OutputStream paramOutputStream1, OutputStream paramOutputStream2) {
        this(paramOutputStream1, paramOutputStream2, true);
    }

    private LauncherOutputStream(OutputStream paramOutputStream1, OutputStream paramOutputStream2, boolean paramBoolean) {
        this.a = paramOutputStream1;
        this.b = paramOutputStream2;
        this.c = paramBoolean;
    }

    public final void write(byte[] paramArrayOfByte, int paramInt1, int paramInt2) throws IOException {
        if (this.a != null) {
            this.a.write(paramArrayOfByte, paramInt1, paramInt2);
        }
        if (this.b != null) {
            this.b.write(paramArrayOfByte, paramInt1, paramInt2);
        }
        if (this.c) {
            flush();
        }
    }

    public final void write(byte[] paramArrayOfByte) throws IOException {
        if (this.a != null) {
            this.a.write(paramArrayOfByte);
        }
        if (this.b != null) {
            this.b.write(paramArrayOfByte);
        }
        if (this.c) {
            flush();
        }
    }

    public final void write(int paramInt) throws IOException {
        if (this.a != null) {
            this.a.write(paramInt);
        }
        if (this.b != null) {
            this.b.write(paramInt);
        }
        if (this.c) {
            flush();
        }
    }

    public final void close() throws IOException {
        flush();

        if (this.a != null) {
            this.a.close();
        }
        if (this.b != null) {
            this.b.close();
        }
    }

    public final void flush() throws IOException {
        if (this.a != null) {
            this.a.flush();
        }
        if (this.b != null) {
            this.b.flush();
        }
    }
}