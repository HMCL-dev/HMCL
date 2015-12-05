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
package org.jackhuang.hellominecraft.logging.logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jackhuang.hellominecraft.logging.AppenderControl;
import org.jackhuang.hellominecraft.logging.Configuration;
import org.jackhuang.hellominecraft.logging.Level;
import org.jackhuang.hellominecraft.logging.LogEvent;
import org.jackhuang.hellominecraft.logging.appender.IAppender;
import org.jackhuang.hellominecraft.logging.message.IMessage;
import org.jackhuang.hellominecraft.logging.message.IMessageFactory;

public class Logger extends AbstractLogger {

    protected volatile PrivateConfig config;
    private final Map<String, AppenderControl> appenders = new ConcurrentHashMap();

    public Logger(String name) {
        this(name, null, Level.INFO);
    }

    public Logger(String name, IMessageFactory messageFactory, Level defaultLevel) {
        super(name, messageFactory);
        this.config = new PrivateConfig(Configuration.DEFAULT, this, defaultLevel);
    }

    public synchronized void setLevel(Level level) {
        if (level != null)
            this.config = new PrivateConfig(this.config, level);
    }

    public Level getLevel() {
        return this.config.level;
    }

    @Override
    public void abstractLog(Level level, IMessage data, Throwable t) {
        LogEvent event = new LogEvent();
        event.level = level;
        event.message = data;
        event.thrown = t;
        event.threadName = Thread.currentThread().getName();

        log(event);
    }

    public void log(LogEvent event) {
        callAppenders(event);
    }

    protected void callAppenders(LogEvent event) {
        for (AppenderControl control : this.appenders.values())
            control.callAppender(event);
    }

    @Override
    public boolean isEnabled(Level level, String msg) {
        return this.config.filter(level, msg);
    }

    @Override
    public boolean isEnabled(Level level, String msg, Throwable t) {
        return this.config.filter(level, msg, t);
    }

    @Override
    public boolean isEnabled(Level level, String msg, Object[] p1) {
        return this.config.filter(level, msg, p1);
    }

    @Override
    public boolean isEnabled(Level level, Object msg, Throwable t) {
        return this.config.filter(level, msg, t);
    }

    @Override
    public boolean isEnabled(Level level, IMessage msg, Throwable t) {
        return this.config.filter(level, msg, t);
    }

    public void addAppender(IAppender appender) {
        this.appenders.put(appender.getName(), new AppenderControl(appender, null));
    }

    public void removeAppender(IAppender appender) {
        this.appenders.remove(appender.getName());
    }

    public Map<String, IAppender> getAppenders() {
        Map map = new HashMap();
        for (Map.Entry entry : this.appenders.entrySet())
            map.put(entry.getKey(), ((AppenderControl) entry.getValue()).getAppender());
        return map;
    }

    @Override
    public String toString() {
        String nameLevel = "" + getName() + ":" + getLevel();
        return nameLevel;
    }

    protected class PrivateConfig {

        public final Configuration config;
        private final Level level;
        private final int intLevel;
        private final Logger logger;

        public PrivateConfig(Configuration c, Logger logger, Level level) {
            this.level = level;
            this.intLevel = this.level.level;
            this.logger = logger;

            this.config = c;
            for (IAppender appender : config.appenders)
                addAppender(appender);
        }

        public PrivateConfig(PrivateConfig pc, Level level) {
            this(pc.config, pc.logger, level);
        }

        boolean filter(Level level, String msg) {

            return this.intLevel >= level.level;
        }

        boolean filter(Level level, String msg, Throwable t) {

            return this.intLevel >= level.level;
        }

        boolean filter(Level level, String msg, Object[] p1) {

            return this.intLevel >= level.level;
        }

        boolean filter(Level level, Object msg, Throwable t) {

            return this.intLevel >= level.level;
        }

        boolean filter(Level level, IMessage msg, Throwable t) {

            return this.intLevel >= level.level;
        }
    }
}
