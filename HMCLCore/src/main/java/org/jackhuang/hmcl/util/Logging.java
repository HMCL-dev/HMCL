/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.*;

/**
 * @author huangyuhui
 */
public final class Logging {
    private Logging() {
    }

    public static final Logger LOG = Logger.getLogger("HMCL");
    private static final ByteArrayOutputStream storedLogs = new ByteArrayOutputStream(IOUtils.DEFAULT_BUFFER_SIZE);

    private static volatile String[] accessTokens = new String[0];

    public static synchronized void registerAccessToken(String token) {
        final String[] oldAccessTokens = accessTokens;
        final String[] newAccessTokens = Arrays.copyOf(oldAccessTokens, oldAccessTokens.length + 1);

        newAccessTokens[oldAccessTokens.length] = token;

        accessTokens = newAccessTokens;
    }

    public static String filterForbiddenToken(String message) {
        for (String token : accessTokens)
            message = message.replace(token, "<access token>");
        return message;
    }

    public static void start(Path logFolder) {
        LOG.setLevel(Level.ALL);
        LOG.setUseParentHandlers(false);
        LOG.setFilter(record -> {
            record.setMessage(filterForbiddenToken(record.getMessage()));
            return true;
        });

        try {
            if (Files.isRegularFile(logFolder))
                Files.delete(logFolder);

            Files.createDirectories(logFolder);
            FileHandler fileHandler = new FileHandler(logFolder.resolve("hmcl.log").toAbsolutePath().toString());
            fileHandler.setLevel(Level.FINEST);
            fileHandler.setFormatter(DefaultFormatter.INSTANCE);
            fileHandler.setEncoding("UTF-8");
            LOG.addHandler(fileHandler);
        } catch (IOException e) {
            System.err.println("Unable to create hmcl.log\n" + StringUtils.getStackTrace(e));
        }

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(DefaultFormatter.INSTANCE);
        consoleHandler.setLevel(Level.FINER);
        LOG.addHandler(consoleHandler);

        StreamHandler streamHandler = new StreamHandler(storedLogs, DefaultFormatter.INSTANCE) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        try {
            streamHandler.setEncoding("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        streamHandler.setLevel(Level.ALL);
        LOG.addHandler(streamHandler);
    }

    public static void initForTest() {
        LOG.setLevel(Level.ALL);
        LOG.setUseParentHandlers(false);

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(DefaultFormatter.INSTANCE);
        consoleHandler.setLevel(Level.FINER);
        LOG.addHandler(consoleHandler);
    }

    public static byte[] getRawLogs() {
        return storedLogs.toByteArray();
    }

    public static String getLogs() {
        try {
            return storedLogs.toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new InternalError(e);
        }
    }

    private static final class DefaultFormatter extends Formatter {

        static final DefaultFormatter INSTANCE = new DefaultFormatter();
        private static final MessageFormat format = new MessageFormat("[{0,date,HH:mm:ss}] [{1}.{2}/{3}] {4}\n");

        @Override
        public String format(LogRecord record) {
            String log = format.format(new Object[]{
                    new Date(record.getMillis()),
                    record.getSourceClassName(), record.getSourceMethodName(), record.getLevel().getName(),
                    record.getMessage()
            }, new StringBuffer(128), null).toString();
            if (record.getThrown() != null)
                log += StringUtils.getStackTrace(record.getThrown());

            return log;
        }

    }
}
