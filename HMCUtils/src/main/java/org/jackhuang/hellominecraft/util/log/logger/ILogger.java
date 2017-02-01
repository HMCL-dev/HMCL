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

/**
 *
 * @author huangyuhui
 */
public interface ILogger {

    void catching(Level paramLevel, Throwable paramThrowable);

    void catching(Throwable paramThrowable);

    void entry();

    void entry(Object[] paramArrayOfObject);

    void error(IMessage paramIMessage);

    void error(IMessage paramIMessage, Throwable paramThrowable);

    void error(Object paramObject);

    void error(Object paramObject, Throwable paramThrowable);

    void error(String paramString);

    void error(String paramString, Object[] paramArrayOfObject);

    void error(String paramString, Throwable paramThrowable);

    void fatal(IMessage paramIMessage);

    void fatal(IMessage paramIMessage, Throwable paramThrowable);

    void fatal(Object paramObject);

    void fatal(Object paramObject, Throwable paramThrowable);

    void fatal(String paramString);

    void fatal(String paramString, Object[] paramArrayOfObject);

    void fatal(String paramString, Throwable paramThrowable);

    String getName();

    void info(IMessage paramIMessage);

    void info(IMessage paramIMessage, Throwable paramThrowable);

    void info(Object paramObject);

    void info(Object paramObject, Throwable paramThrowable);

    void info(String paramString);

    void info(String paramString, Object[] paramArrayOfObject);

    void info(String paramString, Throwable paramThrowable);

    boolean isDebugEnabled();

    boolean isEnabled(Level paramLevel);

    boolean isErrorEnabled();

    boolean isFatalEnabled();

    boolean isInfoEnabled();

    boolean isTraceEnabled();

    boolean isWarnEnabled();

    void log(Level paramLevel, IMessage paramIMessage);

    void log(Level paramLevel, IMessage paramIMessage, Throwable paramThrowable);

    void log(Level paramLevel, Object paramObject);

    void log(Level paramLevel, Object paramObject, Throwable paramThrowable);

    void log(Level paramLevel, String paramString);

    void log(Level paramLevel, String paramString, Object[] paramArrayOfObject);

    void log(Level paramLevel, String paramString, Throwable paramThrowable);

    void printf(Level paramLevel, String paramString, Object[] paramArrayOfObject);

    <T extends Throwable> T throwing(Level paramLevel, T paramT);

    <T extends Throwable> T throwing(T paramT);

    void warn(IMessage paramIMessage);

    void warn(IMessage paramIMessage, Throwable paramThrowable);

    void warn(Object paramObject);

    void warn(Object paramObject, Throwable paramThrowable);

    void warn(String paramString);

    void warn(String paramString, Object[] paramArrayOfObject);

    void warn(String paramString, Throwable paramThrowable);

}
