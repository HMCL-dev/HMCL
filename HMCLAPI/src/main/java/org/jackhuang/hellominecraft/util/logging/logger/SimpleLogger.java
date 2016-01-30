/*
 * Hello Minecraft! Launcher.
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
package org.jackhuang.hellominecraft.util.logging.logger;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.jackhuang.hellominecraft.util.logging.Level;
import org.jackhuang.hellominecraft.util.logging.message.IMessage;
import org.jackhuang.hellominecraft.util.logging.message.IMessageFactory;

public class SimpleLogger extends AbstractLogger {

    private static final char SPACE = ' ';
    private DateFormat dateFormatter;
    private Level level;
    private final boolean showDateTime;
    private final boolean showContextMap;
    private PrintStream stream;
    private final String logName;

    public SimpleLogger(String name, Level defaultLevel, boolean showLogName, boolean showShortLogName, boolean showDateTime, boolean showContextMap, String dateTimeFormat, IMessageFactory messageFactory, PrintStream stream) {
        super(name, messageFactory);
        this.level = defaultLevel;
        if (showShortLogName) {
            int index = name.lastIndexOf(".");
            if ((index > 0) && (index < name.length()))
                this.logName = name.substring(index + 1);
            else
                this.logName = name;
        } else if (showLogName)
            this.logName = name;
        else
            this.logName = null;
        this.showDateTime = showDateTime;
        this.showContextMap = showContextMap;
        this.stream = stream;

        if (showDateTime)
            try {
                this.dateFormatter = new SimpleDateFormat(dateTimeFormat);
            } catch (IllegalArgumentException e) {
                this.dateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS zzz");
            }
    }

    public void setStream(PrintStream stream) {
        this.stream = stream;
    }

    public Level getLevel() {
        return this.level;
    }

    public void setLevel(Level level) {
        if (level != null)
            this.level = level;
    }

    @Override
    public void abstractLog(Level level, IMessage msg, Throwable throwable) {
        StringBuilder sb = new StringBuilder();

        if (this.showDateTime) {
            Date now = new Date();
            String dateText;
            synchronized (this.dateFormatter) {
                dateText = this.dateFormatter.format(now);
            }
            sb.append(dateText);
            sb.append(SPACE);
        }

        sb.append(level.toString());
        sb.append(SPACE);
        if ((this.logName != null) && (this.logName.length() > 0)) {
            sb.append(this.logName);
            sb.append(SPACE);
        }
        sb.append(msg.getFormattedMessage());
        Object[] params = msg.getParameters();
        Throwable t;
        if ((throwable == null) && (params != null) && ((params[(params.length - 1)] instanceof Throwable)))
            t = (Throwable) params[(params.length - 1)];
        else
            t = throwable;
        if (t != null) {
            sb.append(SPACE);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            t.printStackTrace(new PrintStream(baos));
            sb.append(baos.toString());
        }
        this.stream.println(sb.toString());
    }

    @Override
    protected boolean isEnabled(Level level, String msg) {
        return this.level.level >= level.level;
    }

    @Override
    protected boolean isEnabled(Level level, String msg, Throwable t) {
        return this.level.level >= level.level;
    }

    @Override
    protected boolean isEnabled(Level level, String msg, Object[] p1) {
        return this.level.level >= level.level;
    }

    @Override
    protected boolean isEnabled(Level level, Object msg, Throwable t) {
        return this.level.level >= level.level;
    }

    @Override
    protected boolean isEnabled(Level level, IMessage msg, Throwable t) {
        return this.level.level >= level.level;
    }
}
