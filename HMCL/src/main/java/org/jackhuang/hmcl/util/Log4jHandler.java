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

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.xml.parsers.ParserConfigurationException;
import org.jackhuang.hmcl.ui.LogWindow;
import org.jackhuang.hmcl.util.log.Level;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 *
 * @author huang
 */
public class Log4jHandler extends Thread {

    XMLReader reader;
    PipedInputStream inputStream;
    PipedOutputStream outputStream;

    public Log4jHandler(PipedOutputStream outputStream) throws ParserConfigurationException, IOException, SAXException {
        /*SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        .*/
        reader = XMLReaderFactory.createXMLReader();
        inputStream = new PipedInputStream(outputStream);
        this.outputStream = outputStream;
    }

    @Override
    public void run() {
        try {
            outputStream.write("<output>".getBytes());
            outputStream.flush();
            //reader.parse(inputStream, new Log4jHandlerImpl());
            reader.setContentHandler(new Log4jHandlerImpl());
            reader.parse(new InputSource(inputStream));
        } catch (SAXException | IOException e) {

        }
    }

    class Log4jHandlerImpl extends DefaultHandler {
        private final SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");

        String message = "", date = "", thread = "", logger = "";
        Level l = null;
        boolean readingMessage = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            switch (localName) {
                case "log4j_Event":
                    message = "";
                    Date d = new Date(Long.valueOf(attributes.getValue("timestamp")));
                    date = df.format(d);
                    try {
                        l = Level.valueOf(attributes.getValue("level"));
                    } catch(IllegalArgumentException e) {
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
                    println("[" + date + "] [" + thread + "/" + l.name() + "] [" + logger + "] " + message, l);
                    break;
                case "log4j_Message":
                    readingMessage = false;
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            String line = new String(ch, start, length);
            if (line.trim().isEmpty()) return;
            if (readingMessage)
                message += line + C.LINE_SEPARATOR;
            else
                println(line, Level.guessLevel(line));
        }

        public void println(String message, Level l) {
            if (LogWindow.outputStream != null)
                LogWindow.outputStream.log(message, l);
        }
    }
}
