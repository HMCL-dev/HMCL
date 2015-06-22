/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.logging.appender;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jackhuang.hellominecraft.logging.LogEvent;
import org.jackhuang.hellominecraft.logging.LoggingException;
import org.jackhuang.hellominecraft.logging.layout.ILayout;

/**
 *
 * @author hyh
 */
public abstract class OutputStreamAppender extends AbstractAppender {

    protected final OutputStream stream;
    protected final boolean immediateFlush;
    private final Lock readLock = new ReentrantReadWriteLock().readLock();

    public OutputStreamAppender(String name, ILayout<? extends Serializable> layout, boolean ignoreExceptions, OutputStream stream, boolean immediateFlush) {
	super(name, layout, ignoreExceptions);

	this.immediateFlush = immediateFlush;
	this.stream = stream;
    }

    @Override
    public void append(LogEvent event) {
	this.readLock.lock();
	try {
	    byte[] bytes = getLayout().toByteArray(event);
	    if (bytes.length > 0) {
		stream.write(bytes);
	    }
	    if(event.thrown != null)
		event.thrown.printStackTrace(new PrintStream(stream));
	} catch (IOException ex) {
	    System.err.println("Unable to write to stream for appender: " + getName());
	    throw new LoggingException(ex);
	} finally {
	    this.readLock.unlock();
	}
    }
}
