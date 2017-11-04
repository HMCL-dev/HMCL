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
package org.jackhuang.hmcl.util;

import java.io.PipedOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jackhuang.hmcl.api.HMCLApi;
import org.jackhuang.hmcl.api.event.process.JavaProcessStoppedEvent;
import org.jackhuang.hmcl.api.func.Consumer;
import org.jackhuang.hmcl.ui.LogWindow;
import org.jackhuang.hmcl.util.log.Level;
import org.jackhuang.hmcl.util.sys.ProcessMonitor;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author huang
 */
public class Log4jHandler extends Thread implements Consumer<JavaProcessStoppedEvent> {

    ProcessMonitor monitor;
    List<Pair<String, String>> forbiddenTokens = new LinkedList<>();
    AtomicBoolean interrupted = new AtomicBoolean(false);
    boolean enabled = true;

    public Log4jHandler(ProcessMonitor monitor, PipedOutputStream outputStream, boolean enabled) {
        this.monitor = monitor;
        this.enabled = enabled;

        HMCLApi.EVENT_BUS.channel(JavaProcessStoppedEvent.class).register((Consumer<JavaProcessStoppedEvent>) this);
    }

    public void addForbiddenToken(String token, String replacement) {
        forbiddenTokens.add(new Pair<>(token, replacement));
    }

    @Override
    public void run() {
    }

    @Override
    public void accept(JavaProcessStoppedEvent t) {
    }

    /**
     * Always use this instance method to write to the log,
     * to make sure only this Thread writes to the PipedOutputStream.
     *
     * It will also flush the buffer, directly after write.
     *
     * This prevents java.io.IOException: Read end dead
     * See also https://techtavern.wordpress.com/2008/07/16/whats-this-ioexception-write-end-dead/
     *
     * @param content The content to be written to the log
     */
    public Future newLogLine(String content) {
        printlnImpl(content, Level.guessLevel(content));
        return null;
    }

    class Log4jHandlerImpl extends DefaultHandler {

        private final SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");

        String date = "", thread = "", logger = "";
        StringBuilder message = null;
        Level l = null;
        boolean readingMessage = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            switch (localName) {
                case "log4j_Event":
                    message = new StringBuilder();
                    Date d = new Date(Long.valueOf(attributes.getValue("timestamp")));
                    date = df.format(d);
                    try {
                        l = Level.valueOf(attributes.getValue("level"));
                    } catch (IllegalArgumentException e) {
                        l = Level.INFO;
                    }
                    thread = attributes.getValue("thread");
                    logger = attributes.getValue("logger");
                    if ("STDERR".equals(logger))
                        l = Level.ERROR;
                    break;
                case "log4j_Message":
                    readingMessage = true;
                    break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            switch (localName) {
                case "log4j_Event":
                    println("[" + date + "] [" + thread + "/" + l.name() + "] [" + logger + "] " + message.toString(), l);
                    break;
                case "log4j_Message":
                    readingMessage = false;
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            String line = new String(ch, start, length);
            if (line.trim().isEmpty())
                return;
            if (readingMessage)
                message.append(line).append(C.LINE_SEPARATOR);
            else
                println(line, Level.guessLevel(line));
        }

        public void println(String message, Level l) {
            printlnImpl(message, l);
        }
    }

    private void printlnImpl(String message, Level l) {
        for (Pair<String, String> entry : forbiddenTokens)
            message = message.replace(entry.key, entry.value);
        if (LogWindow.outputStream != null)
            LogWindow.outputStream.log(message, l);
    }
}
