/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.logging.message;

/**
 *
 * @author hyh
 */
public class SimpleMessage
	implements IMessage {

    private static final long serialVersionUID = -8398002534962715992L;
    private final String message;

    public SimpleMessage() {
	this(null);
    }

    public SimpleMessage(String message) {
	this.message = message;
    }

    @Override
    public String getFormattedMessage() {
	return this.message;
    }

    @Override
    public String getFormat() {
	return this.message;
    }

    @Override
    public Object[] getParameters() {
	return null;
    }

    @Override
    public boolean equals(Object o) {
	if (this == o) {
	    return true;
	}
	if ((o == null) || (getClass() != o.getClass())) {
	    return false;
	}

	SimpleMessage that = (SimpleMessage) o;

	return this.message != null ? this.message.equals(that.message) : that.message == null;
    }

    @Override
    public int hashCode() {
	return this.message != null ? this.message.hashCode() : 0;
    }

    @Override
    public String toString() {
	return "SimpleMessage[message=" + this.message + "]";
    }

    @Override
    public Throwable getThrowable() {
	return null;
    }
}
