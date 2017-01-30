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
package org.jackhuang.hellominecraft.util.log.logger;

import org.jackhuang.hellominecraft.util.log.Level;
import org.jackhuang.hellominecraft.util.log.message.IMessage;
import org.jackhuang.hellominecraft.util.log.message.IMessageFactory;
import org.jackhuang.hellominecraft.util.log.message.ParameterizedMessageFactory;
import org.jackhuang.hellominecraft.util.log.message.StringFormattedMessage;

public abstract class AbstractLogger
    implements ILogger {

    public static final Class<? extends IMessageFactory> DEFAULT_MESSAGE_FACTORY_CLASS = ParameterizedMessageFactory.class;

    private static final String THROWING = "throwing";
    private static final String CATCHING = "catching";
    private final String name;
    private final IMessageFactory messageFactory;

    public AbstractLogger() {
        this.name = getClass().getName();
        this.messageFactory = createDefaultMessageFactory();
    }

    public AbstractLogger(String name) {
        this.name = name;
        this.messageFactory = createDefaultMessageFactory();
    }

    public AbstractLogger(String name, IMessageFactory messageFactory) {
        this.name = name;
        this.messageFactory = (messageFactory == null ? createDefaultMessageFactory() : messageFactory);
    }

    private IMessageFactory createDefaultMessageFactory() {
        try {
            return (IMessageFactory) DEFAULT_MESSAGE_FACTORY_CLASS.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void catching(Level level, Throwable t) {
        if (isEnabled(level, (Object) null, null))
            log(level, this.messageFactory.newMessage(CATCHING), t);
    }

    @Override
    public void catching(Throwable t) {
        catching(Level.ERROR, t);
    }

    @Override
    public void entry() {
        entry(new Object[0]);
    }

    @Override
    public void entry(Object[] params) {
        if (isEnabled(Level.TRACE, (Object) null, null))
            log(Level.TRACE, entryMsg(params.length, params), null);
    }

    private IMessage entryMsg(int count, Object[] params) {
        if (count == 0)
            return this.messageFactory.newMessage("entry");
        StringBuilder sb = new StringBuilder("entry params(");
        int i = 0;
        for (Object parm : params) {
            if (parm != null)
                sb.append(parm.toString());
            else
                sb.append("null");
            i++;
            if (i < params.length)
                sb.append(", ");
        }
        sb.append(")");
        return this.messageFactory.newMessage(sb.toString());
    }

    @Override
    public void error(IMessage msg) {
        if (isEnabled(Level.ERROR, msg, null))
            log(Level.ERROR, msg, null);
    }

    @Override
    public void error(IMessage msg, Throwable t) {
        if (isEnabled(Level.ERROR, msg, t))
            log(Level.ERROR, msg, t);
    }

    @Override
    public void error(Object message) {
        if (isEnabled(Level.ERROR, message, null))
            log(Level.ERROR, this.messageFactory.newMessage(message), null);
    }

    @Override
    public void error(Object message, Throwable t) {
        if (isEnabled(Level.ERROR, message, t))
            log(Level.ERROR, this.messageFactory.newMessage(message), t);
    }

    @Override
    public void error(String message) {
        if (isEnabled(Level.ERROR, message))
            log(Level.ERROR, this.messageFactory.newMessage(message), null);
    }

    @Override
    public void error(String message, Object[] params) {
        if (isEnabled(Level.ERROR, message, params)) {
            IMessage msg = this.messageFactory.newMessage(message, params);
            log(Level.ERROR, msg, msg.getThrowable());
        }
    }

    @Override
    public void error(String message, Throwable t) {
        if (isEnabled(Level.ERROR, message, t))
            log(Level.ERROR, this.messageFactory.newMessage(message), t);
    }

    @Override
    public void fatal(IMessage msg) {
        if (isEnabled(Level.FATAL, msg, null))
            log(Level.FATAL, msg, null);
    }

    @Override
    public void fatal(IMessage msg, Throwable t) {
        if (isEnabled(Level.FATAL, msg, t))
            log(Level.FATAL, msg, t);
    }

    @Override
    public void fatal(Object message) {
        if (isEnabled(Level.FATAL, message, null))
            log(Level.FATAL, this.messageFactory.newMessage(message), null);
    }

    @Override
    public void fatal(Object message, Throwable t) {
        if (isEnabled(Level.FATAL, message, t))
            log(Level.FATAL, this.messageFactory.newMessage(message), t);
    }

    @Override
    public void fatal(String message) {
        if (isEnabled(Level.FATAL, message))
            log(Level.FATAL, this.messageFactory.newMessage(message), null);
    }

    @Override
    public void fatal(String message, Object[] params) {
        if (isEnabled(Level.FATAL, message, params)) {
            IMessage msg = this.messageFactory.newMessage(message, params);
            log(Level.FATAL, msg, msg.getThrowable());
        }
    }

    @Override
    public void fatal(String message, Throwable t) {
        if (isEnabled(Level.FATAL, message, t))
            log(Level.FATAL, this.messageFactory.newMessage(message), t);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void info(IMessage msg) {
        if (isEnabled(Level.INFO, msg, null))
            log(Level.INFO, msg, null);
    }

    @Override
    public void info(IMessage msg, Throwable t) {
        if (isEnabled(Level.INFO, msg, t))
            log(Level.INFO, msg, t);
    }

    @Override
    public void info(Object message) {
        if (isEnabled(Level.INFO, message, null))
            log(Level.INFO, this.messageFactory.newMessage(message), null);
    }

    @Override
    public void info(Object message, Throwable t) {
        if (isEnabled(Level.INFO, message, t))
            log(Level.INFO, this.messageFactory.newMessage(message), t);
    }

    @Override
    public void info(String message) {
        if (isEnabled(Level.INFO, message))
            log(Level.INFO, this.messageFactory.newMessage(message), null);
    }

    @Override
    public void info(String message, Object[] params) {
        if (isEnabled(Level.INFO, message, params)) {
            IMessage msg = this.messageFactory.newMessage(message, params);
            log(Level.INFO, msg, msg.getThrowable());
        }
    }

    @Override
    public void info(String message, Throwable t) {
        if (isEnabled(Level.INFO, message, t))
            log(Level.INFO, this.messageFactory.newMessage(message), t);
    }

    protected abstract boolean isEnabled(Level paramLevel, IMessage paramIMessage, Throwable paramThrowable);

    protected abstract boolean isEnabled(Level paramLevel, Object paramObject, Throwable paramThrowable);

    protected abstract boolean isEnabled(Level paramLevel, String paramString);

    protected abstract boolean isEnabled(Level paramLevel, String paramString, Object[] paramArrayOfObject);

    protected abstract boolean isEnabled(Level paramLevel, String paramString, Throwable paramThrowable);

    protected abstract void abstractLog(Level level, IMessage msg, Throwable t);

    @Override
    public boolean isErrorEnabled() {
        return isEnabled(Level.ERROR);
    }

    @Override

    public boolean isFatalEnabled() {
        return isEnabled(Level.FATAL);
    }

    @Override
    public boolean isInfoEnabled() {
        return isEnabled(Level.INFO);
    }

    @Override
    public boolean isTraceEnabled() {
        return isEnabled(Level.TRACE);
    }

    @Override
    public boolean isWarnEnabled() {
        return isEnabled(Level.WARN);
    }

    @Override
    public boolean isDebugEnabled() {
        return isEnabled(Level.DEBUG);
    }

    @Override
    public boolean isEnabled(Level level) {
        return isEnabled(level, (Object) null, null);
    }

    @Override
    public void log(Level level, IMessage msg) {
        if (isEnabled(level, msg, null))
            log(level, msg, null);
    }

    @Override
    public void log(Level level, IMessage msg, Throwable t) {
        if (isEnabled(level, msg, t))
            abstractLog(level, msg, t);
    }

    @Override
    public void log(Level level, Object message) {
        if (isEnabled(level, message, null))
            log(level, this.messageFactory.newMessage(message), null);
    }

    @Override
    public void log(Level level, Object message, Throwable t) {
        if (isEnabled(level, message, t))
            log(level, this.messageFactory.newMessage(message), t);
    }

    @Override
    public void log(Level level, String message) {
        if (isEnabled(level, message))
            log(level, this.messageFactory.newMessage(message), null);
    }

    @Override
    public void log(Level level, String message, Object[] params) {
        if (isEnabled(level, message, params)) {
            IMessage msg = this.messageFactory.newMessage(message, params);
            log(level, msg, msg.getThrowable());
        }
    }

    @Override
    public void log(Level level, String message, Throwable t) {
        if (isEnabled(level, message, t))
            log(level, this.messageFactory.newMessage(message), t);
    }

    @Override
    public void printf(Level level, String format, Object[] params) {
        if (isEnabled(level, format, params)) {
            IMessage msg = new StringFormattedMessage(format, params);
            log(level, msg, msg.getThrowable());
        }
    }

    @Override
    public <T extends Throwable> T throwing(T t) {
        return throwing(Level.ERROR, t);
    }

    @Override
    public <T extends Throwable> T throwing(Level level, T t) {
        if (isEnabled(level, (Object) null, null))
            log(level, this.messageFactory.newMessage(THROWING), t);
        return t;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public void warn(IMessage msg) {
        if (isEnabled(Level.WARN, msg, null))
            log(Level.WARN, msg, null);
    }

    @Override
    public void warn(IMessage msg, Throwable t) {
        if (isEnabled(Level.WARN, msg, t))
            log(Level.WARN, msg, t);
    }

    @Override
    public void warn(Object message) {
        if (isEnabled(Level.WARN, message, null))
            log(Level.WARN, this.messageFactory.newMessage(message), null);
    }

    @Override
    public void warn(Object message, Throwable t) {
        if (isEnabled(Level.WARN, message, t))
            log(Level.WARN, this.messageFactory.newMessage(message), t);
    }

    @Override
    public void warn(String message) {
        if (isEnabled(Level.WARN, message))
            log(Level.WARN, this.messageFactory.newMessage(message), null);
    }

    @Override
    public void warn(String message, Object[] params) {
        if (isEnabled(Level.WARN, message, params)) {
            IMessage msg = this.messageFactory.newMessage(message, params);
            log(Level.WARN, msg, msg.getThrowable());
        }
    }

    @Override
    public void warn(String message, Throwable t) {
        if (isEnabled(Level.WARN, message, t))
            log(Level.WARN, this.messageFactory.newMessage(message), t);
    }
}
