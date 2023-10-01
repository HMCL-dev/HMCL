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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.*;

/**
 * @author huangyuhui, Buring_TNT
 */
public final class Logging {
    private Logging() {
    }

    public static final Logger LOG = Logger.getLogger("HMCL");
    private static final ByteArrayOutputStream storedLogs = new ByteArrayOutputStream(IOUtils.DEFAULT_BUFFER_SIZE);

    private static volatile String[] accessTokens = new String[16];
    private static volatile int accessTokenSize = 0;

    public static synchronized void registerAccessToken(String token) {
        if (token == null) {
            throw new NullPointerException("Cannot add a null access token");
        }
        for (int i = 0; i < accessTokenSize; i++) {
            if (accessTokens[i].equals(token)) {
                return;
            }
        }
        if (accessTokens.length == accessTokenSize) {
            final String[] oldAccessTokens = accessTokens;
            final String[] newAccessTokens = new String[accessTokenSize + 1];
            System.arraycopy(oldAccessTokens, 0, newAccessTokens, 0, accessTokenSize);
            newAccessTokens[accessTokenSize] = token;

            accessTokens = newAccessTokens;
        } else {
            accessTokens[accessTokenSize] = token;
        }
        accessTokenSize++;
    }

    public static String filterForbiddenToken(String message) {
        // In registerAccessToken, we always modify accessTokenSize after accessTokens is already modified.
        // Which means, if accessTokenSize is greater than 0, accessTokens always contain enough data,
        //   which won't cause any useless copy action between StringBuilder and String.
        if (accessTokenSize == 0) {
            return message;
        }

        // Usually, the access token is longer than "<access token>", therefore, we're able to allocate enough space in advance.
        StringBuilder first = new StringBuilder(message);
        StringBuilder second = new StringBuilder(message.length());
        for (String token : accessTokens) {
            if (token == null) {
                break;
            }

            for (int lastIndex = 0;;) {
                int index = first.indexOf(token, lastIndex);
                if (index == -1) {
                    second.append(first, lastIndex, first.length());
                    break;
                } else {
                    second.append(first, lastIndex, index);
                    second.append("<access token>");
                    lastIndex = index + token.length();
                }
            }
            StringBuilder tmp = first;
            first = second;
            second = tmp;
        }
        return first.toString();
    }

    public static void start(Path logFolder) {
        LOG.setLevel(Level.ALL);
        LOG.setUseParentHandlers(false);
        LOG.setFilter(record -> {
            record.setMessage(format(record));
            return true;
        });

        DefaultFormatter formatter = new DefaultFormatter();

        try {
            if (Files.isRegularFile(logFolder))
                Files.delete(logFolder);

            Files.createDirectories(logFolder);
            FileHandler fileHandler = new FileHandler(logFolder.resolve("hmcl.log").toAbsolutePath().toString());
            fileHandler.setLevel(Level.FINEST);
            fileHandler.setFormatter(formatter);
            fileHandler.setEncoding("UTF-8");
            LOG.addHandler(fileHandler);
        } catch (IOException e) {
            System.err.println("Unable to create hmcl.log\n" + StringUtils.getStackTrace(e));
        }

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(formatter);
        consoleHandler.setLevel(Level.FINER);
        LOG.addHandler(consoleHandler);

        StreamHandler streamHandler = new StreamHandler(storedLogs, formatter) {
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
        consoleHandler.setFormatter(new DefaultFormatter());
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

    private static final MessageFormat FORMAT = new MessageFormat("[{0,date,HH:mm:ss}] [{1}.{2}/{3}] {4}\n");

    private static String format(LogRecord record) {
        String message = filterForbiddenToken(record.getMessage());

        Throwable thrown = record.getThrown();

        StringWriter writer;
        StringBuffer buffer;
        if (thrown == null) {
            writer = null;
            buffer = new StringBuffer(256);
        } else {
            writer = new StringWriter(1024);
            buffer = writer.getBuffer();
        }

        FORMAT.format(new Object[]{
                new Date(record.getMillis()),
                record.getSourceClassName(), record.getSourceMethodName(), record.getLevel().getName(),
                message
        }, buffer, null);

        if (thrown != null) {
            try (PrintWriter printWriter = new PrintWriter(writer)) {
                thrown.printStackTrace(printWriter);
            }
        }
        return buffer.toString();
    }

    private static final class DefaultFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return record.getMessage();
        }
    }
}
