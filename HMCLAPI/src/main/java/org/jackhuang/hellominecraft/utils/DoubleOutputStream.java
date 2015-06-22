/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import java.io.IOException;
import java.io.OutputStream;

/**
 *
 * @author hyh
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
	if (this.a != null) {
	    this.a.write(arr, off, len);
	}
	if (this.b != null) {
	    this.b.write(arr, off, len);
	}
	if (this.c) {
	    flush();
	}
    }

    @Override
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

    @Override
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

    @Override
    public final void close() throws IOException {
	flush();

	if (this.a != null) {
	    this.a.close();
	}
	if (this.b != null) {
	    this.b.close();
	}
    }

    @Override
    public final void flush() throws IOException {
	if (this.a != null) {
	    this.a.flush();
	}
	if (this.b != null) {
	    this.b.flush();
	}
    }
}
