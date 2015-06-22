/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.logging.appender;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import org.jackhuang.hellominecraft.logging.layout.ILayout;

/**
 *
 * @author hyh
 */
public class ConsoleAppender extends OutputStreamAppender {

    public ConsoleAppender(String name, ILayout<? extends Serializable> layout, boolean ignoreExceptions, OutputStream stream, boolean immediateFlush) {
	super(name, layout, ignoreExceptions, stream, true);
    }

    public static class SystemOutStream extends OutputStream {

	@Override
	public void close() {
	}

	@Override
	public void flush() {
	    System.out.flush();
	}

	@Override
	public void write(byte[] b) throws IOException {
	    System.out.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len)
		throws IOException {
	    System.out.write(b, off, len);
	}

	@Override
	public void write(int b) throws IOException {
	    System.out.write(b);
	}
    }

    public static class SystemErrStream extends OutputStream {

	@Override
	public void close() {
	}

	@Override
	public void flush() {
	    System.err.flush();
	}

	@Override
	public void write(byte[] b) throws IOException {
	    System.err.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len)
		throws IOException {
	    System.err.write(b, off, len);
	}

	@Override
	public void write(int b) {
	    System.err.write(b);
	}
    }
}
