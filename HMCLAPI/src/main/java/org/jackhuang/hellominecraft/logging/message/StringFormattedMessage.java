/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.logging.message;

import java.util.Arrays;
import java.util.IllegalFormatException;

public class StringFormattedMessage
	implements IMessage {

    private static final long serialVersionUID = -665975803997290697L;
    private final String messagePattern;
    private final transient Object[] argArray;
    private String[] stringArgs;
    private transient String formattedMessage;
    private transient Throwable throwable;

    public StringFormattedMessage(String messagePattern, Object[] arguments) {
	this.messagePattern = messagePattern;
	this.argArray = arguments;
	if ((arguments != null) && (arguments.length > 0) && ((arguments[(arguments.length - 1)] instanceof Throwable))) {
	    this.throwable = ((Throwable) arguments[(arguments.length - 1)]);
	}
    }

    @Override
    public String getFormattedMessage() {
	if (this.formattedMessage == null) {
	    this.formattedMessage = formatMessage(this.messagePattern, this.argArray);
	}
	return this.formattedMessage;
    }

    @Override
    public String getFormat() {
	return this.messagePattern;
    }

    @Override
    public Object[] getParameters() {
	if (this.argArray != null) {
	    return this.argArray;
	}
	return this.stringArgs;
    }

    protected String formatMessage(String msgPattern, Object[] args) {
	try {
	    return String.format(msgPattern, args);
	} catch (IllegalFormatException ife) {
	    System.err.println("Unable to format msg: " + msgPattern);
	    ife.printStackTrace();
	}
	return msgPattern;
    }

    @Override
    public boolean equals(Object o) {
	if (this == o) {
	    return true;
	}
	if ((o == null) || (getClass() != o.getClass())) {
	    return false;
	}

	StringFormattedMessage that = (StringFormattedMessage) o;

	if (this.messagePattern != null ? !this.messagePattern.equals(that.messagePattern) : that.messagePattern != null) {
	    return false;
	}

	return Arrays.equals(this.stringArgs, that.stringArgs);
    }

    @Override
    public int hashCode() {
	int result = this.messagePattern != null ? this.messagePattern.hashCode() : 0;
	result = 31 * result + (this.stringArgs != null ? Arrays.hashCode(this.stringArgs) : 0);
	return result;
    }

    @Override
    public String toString() {
	return "StringFormatMessage[messagePattern=" + this.messagePattern + ", args=" + Arrays.toString(this.argArray) + "]";
    }

    @Override
    public Throwable getThrowable() {
	return this.throwable;
    }
}
