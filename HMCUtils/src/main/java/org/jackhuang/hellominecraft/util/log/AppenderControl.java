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
package org.jackhuang.hellominecraft.util.log;

import org.jackhuang.hellominecraft.util.log.appender.IAppender;

public class AppenderControl {

    private final ThreadLocal<AppenderControl> recursive = new ThreadLocal<>();
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
        if (this.level != null
            && this.intLevel < event.level.level)
            return;

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
                if (!this.appender.ignoreExceptions())
                    throw ex;
            } catch (Exception ex) {
                System.err.println("An exception occurred processing Appender " + this.appender.getName());
                ex.printStackTrace();
                if (!this.appender.ignoreExceptions())
                    throw new LoggingException(ex);
            }
        } finally {
            this.recursive.set(null);
        }
    }
}
