/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.logging;

import org.jackhuang.hellominecraft.logging.appender.IAppender;

public class AppenderControl {

    private final ThreadLocal<AppenderControl> recursive = new ThreadLocal();
    private final IAppender appender;
    private final Level level;
    private final int intLevel;

    public AppenderControl(IAppender appender, Level level) {
	this.appender = appender;
	this.level = level;
	this.intLevel = (level == null ? Level.ALL.level : level.level);
    }

    public IAppender getAppender() {
	return this.appender;
    }

    public void callAppender(LogEvent event) {
	if ((this.level != null)
		&& (this.intLevel < event.level.level)) {
	    return;
	}

	if (this.recursive.get() != null) {
	    System.err.println("Recursive call to appender " + this.appender.getName());
	    return;
	}
	try {
	    this.recursive.set(this);

	    try {
		this.appender.append(event);
	    } catch (RuntimeException ex) {
		System.err.println("An exception occurred processing Appender " + this.appender.getName());
		ex.printStackTrace();
		if (!this.appender.ignoreExceptions()) {
		    throw ex;
		}
	    } catch (Exception ex) {
		System.err.println("An exception occurred processing Appender " + this.appender.getName());
		ex.printStackTrace();
		if (!this.appender.ignoreExceptions()) {
		    throw new LoggingException(ex);
		}
	    }
	} finally {
	    this.recursive.set(null);
	}
    }
}
