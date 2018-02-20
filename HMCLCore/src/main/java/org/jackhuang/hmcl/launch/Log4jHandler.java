/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.*;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * This class is to parse log4j classic XML layout logging,
 * since only vanilla Minecraft will enable this layout.
 *
 * Also supports plain logs.
 *
 * @author huangyuhui
 */
final class Log4jHandler extends Thread {

    private final XMLReader reader;
    private final BiConsumer<String, Log4jLevel> callback;
    private final PipedOutputStream outputStream = new PipedOutputStream();
    private final PipedInputStream inputStream = Lang.invoke(() -> new PipedInputStream(outputStream));
    private final AtomicBoolean interrupted = new AtomicBoolean(false);
    private final List<String> logs = new LinkedList<>();

    public Log4jHandler(BiConsumer<String, Log4jLevel> callback) {
        this.callback = callback;
        newLine("<output>");

        reader = Lang.invoke((ExceptionalSupplier<XMLReader, SAXException>) XMLReaderFactory::createXMLReader);
        reader.setContentHandler(new Log4jHandlerImpl());
    }

    @Override
    public void run() {
        setName("log4j-handler");

        try {
            reader.parse(new InputSource(inputStream));
        } catch (InterruptedIOException e) {
            // Game has been interrupted.
            interrupted.set(true);
        } catch (SAXException | IOException e) {
            Logging.LOG.log(Level.WARNING, "An error occurred when reading console lines", e);
        }
    }

    public void onStopped() {
        if (interrupted.get())
            return;

        Lang.invoke(() -> Schedulers.newThread().schedule(() -> {
            if (!interrupted.get()) {
                newLine("</output>").get();
                outputStream.close();
                join();
            }
        }).get());
    }

    public Future<?> newLine(String log) {
        return Schedulers.computation().schedule(() -> {
            try {
                String line = (log + OperatingSystem.LINE_SEPARATOR)
                        .replace("<![CDATA[", "")
                        .replace("]]>", "")
                        .replace("log4j:Event", "log4j_Event")
                        .replace("<log4j:Message>", "<log4j_Message><![CDATA[")
                        .replace("</log4j:Message>", "]]></log4j_Message>")
                        .replace("log4j:Throwable", "log4j_Throwable");
                logs.add(line);
                byte[] bytes = line.getBytes(Charsets.UTF_8);
                outputStream.write(bytes);
                outputStream.flush();
            } catch (IOException e) {
                // Ignoring IOException, including read end dead.
                Logging.LOG.log(Level.WARNING, "An error occurred when writing console lines", e);
            }
        });
    }

    private class Log4jHandlerImpl extends DefaultHandler {

        private String date = "", thread = "", logger = "";
        private StringBuilder message;
        private Log4jLevel level;
        private boolean readingMessage = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            switch (localName) {
                case "log4j_Event":
                    message = new StringBuilder();
                    Date d = new Date(Long.valueOf(attributes.getValue("timestamp")));
                    date = Constants.DEFAULT_DATE_FORMAT.format(d);
                    try {
                        level = Log4jLevel.valueOf(attributes.getValue("level"));
                    } catch (IllegalArgumentException e) {
                        level = Log4jLevel.INFO;
                    }
                    thread = attributes.getValue("thread");
                    logger = attributes.getValue("logger");
                    if ("STDERR".equals(logger))
                        level = Log4jLevel.ERROR;
                    break;
                case "log4j_Message":
                    readingMessage = true;
                    break;
                case "log4j_Throwable":
                    break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (localName) {
                case "log4j_Event":
                    callback.accept("[" + date + "] [" + thread + "/" + level.name() + "] [" + logger + "] " + message.toString(), level);
                    break;
                case "log4j_Message":
                    readingMessage = false;
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            String line = new String(ch, start, length);
            if (line.trim().isEmpty())
                return;
            if (readingMessage)
                message.append(line).append(OperatingSystem.LINE_SEPARATOR);
            else
                callback.accept(line, Optional.ofNullable(Log4jLevel.guessLevel(line)).orElse(Log4jLevel.INFO));
        }
    }
}
