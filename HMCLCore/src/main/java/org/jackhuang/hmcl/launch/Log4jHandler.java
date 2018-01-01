/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Date;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.OperatingSystem;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

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

    public Log4jHandler(BiConsumer<String, Log4jLevel> callback) {
        this.callback = callback;

        reader = Lang.invoke(() -> XMLReaderFactory.createXMLReader());
        reader.setContentHandler(new Log4jHandlerImpl());
    }

    @Override
    public void run() {
        setName("log4j-handler");
        newLine("<output>");

        try {
            reader.parse(new InputSource(inputStream));
        } catch (InterruptedIOException e) {
            // Game has been interrupted.
            interrupted.set(true);
        } catch (SAXException | IOException e) {
            Lang.throwable(e);
        }
    }

    public void onStopped() {
        if (interrupted.get())
            return;

        Lang.invoke(() -> Schedulers.newThread().schedule(() -> {
            if (!interrupted.get()) {
                Lang.invoke(() -> newLine("</output>").get());
                outputStream.close();
                join();
            }
        }).get());
    }

    public Future<?> newLine(String content) {
        return Schedulers.computation().schedule(() -> {
            String log = content;
            if (!log.trim().startsWith("<"))
                log = "<![CDATA[" + log.replace("]]>", "") + "]]>";
            outputStream.write((log + OperatingSystem.LINE_SEPARATOR)
                    .replace("log4j:Event", "log4j_Event")
                    .replace("log4j:Message", "log4j_Message")
                    .replace("log4j:Throwable", "log4j_Throwable")
                    .getBytes()
            );
            outputStream.flush();
        });
    }

    private class Log4jHandlerImpl extends DefaultHandler {

        private String date = "", thread = "", logger = "";
        private StringBuilder message;
        private Log4jLevel level;
        private boolean readingMessage = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
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
        public void endElement(String uri, String localName, String qName) throws SAXException {
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
        public void characters(char[] ch, int start, int length) throws SAXException {
            String line = new String(ch, start, length);
            if (line.trim().isEmpty())
                return;
            if (readingMessage)
                message.append(line).append(OperatingSystem.LINE_SEPARATOR);
            else
                callback.accept(line, Lang.nonNull(Log4jLevel.guessLevel(line), Log4jLevel.INFO));
        }
    }
}
