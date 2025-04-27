/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.io;

import org.glavo.chardet.DetectedCharset;
import org.glavo.chardet.UniversalDetector;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.*;

/**
 * This utility class consists of some util methods operating on InputStream/OutputStream.
 *
 * @author huangyuhui
 */
public final class IOUtils {

    private IOUtils() {
    }

    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    private static boolean isASCIICompatible(Charset charset) {
        if (charset == US_ASCII || charset == UTF_8 || charset.name().startsWith("ISO-8859-")) {
            return true;
        }

        if (charset.name().startsWith("UTF-")) {
            return false;
        }

        switch (charset.name().toLowerCase(Locale.ROOT)) {
            case "gbk":
            case "gb2312":
            case "gb18030":
            case "big5":
            case "euc-kr":
                return true;
            case "shift-jis":
                return false;
            default:
                byte[] buffer = new byte[128];
                for (int i = 0; i < buffer.length; i++) {
                    buffer[i] = (byte) i;
                }

                String str = new String(buffer, charset);
                if (str.length() != buffer.length)
                    return false;

                for (int i = 0; i < buffer.length; i++) {
                    if (str.charAt(i) != buffer[i])
                        return false;
                }

                return true;
        }
    }

    public static BufferedReader newBufferedReaderByDetectedCharset(Path file, Charset defaultCharset) throws IOException {
        FileChannel channel = FileChannel.open(file);
        try {
            long oldPosition = channel.position();

            DetectedCharset detectedCharset = UniversalDetector.detectCharset(channel);

            Charset charset;
            if (detectedCharset != null) {
                if (detectedCharset.isSupported()) {
                    charset = detectedCharset.getCharset();
                    if (charset == US_ASCII) {
                        charset = isASCIICompatible(defaultCharset) ? defaultCharset : UTF_8;
                    }
                } else if (detectedCharset == DetectedCharset.HZ_GB_2312) {
                    charset = Charset.forName("GB18030");
                } else if (detectedCharset == DetectedCharset.X_ISO_10646_UCS_4_2143 || detectedCharset == DetectedCharset.X_ISO_10646_UCS_4_3412) {
                    charset = Charset.forName("UTF-32");
                } else {
                    charset = defaultCharset;
                }
            } else {
                charset = defaultCharset;
            }

            channel.position(oldPosition);
            return new BufferedReader(new InputStreamReader(Channels.newInputStream(channel), charset));
        } catch (Throwable e) {
            closeQuietly(channel, e);
            throw e;
        }
    }

    /**
     * Read all bytes to a buffer from given input stream. The stream will not be closed.
     *
     * @param stream the InputStream being read.
     * @return all bytes read from the stream
     * @throws IOException if an I/O error occurs.
     */
    public static byte[] readFullyWithoutClosing(InputStream stream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream(Math.max(stream.available(), 32));
        copyTo(stream, result);
        return result.toByteArray();
    }

    public static String readFullyAsStringWithClosing(InputStream stream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream(Math.max(stream.available(), 32));
        copyTo(stream, result);
        return result.toString("UTF-8");
    }

    /**
     * Read all bytes to a buffer from given input stream, and close the input stream finally.
     *
     * @param stream the InputStream being read, closed finally.
     * @return all bytes read from the stream
     * @throws IOException if an I/O error occurs.
     */
    public static ByteArrayOutputStream readFully(InputStream stream) throws IOException {
        try (InputStream is = stream) {
            ByteArrayOutputStream result = new ByteArrayOutputStream(Math.max(is.available(), 32));
            copyTo(is, result);
            return result;
        }
    }

    public static byte[] readFullyAsByteArray(InputStream stream) throws IOException {
        return readFully(stream).toByteArray();
    }

    public static String readFullyAsString(InputStream stream) throws IOException {
        return readFully(stream).toString("UTF-8");
    }

    public static String readFullyAsString(InputStream stream, Charset charset) throws IOException {
        return readFully(stream).toString(charset.name());
    }

    public static void copyTo(InputStream src, OutputStream dest) throws IOException {
        copyTo(src, dest, new byte[DEFAULT_BUFFER_SIZE]);
    }

    public static void copyTo(InputStream src, OutputStream dest, byte[] buf) throws IOException {
        while (true) {
            int len = src.read(buf);
            if (len == -1)
                break;
            dest.write(buf, 0, len);
        }
    }

    public static InputStream wrapFromGZip(InputStream inputStream) throws IOException {
        return new GZIPInputStream(inputStream);
    }

    public static void closeQuietly(AutoCloseable closeable) {
        try {
            if (closeable != null)
                closeable.close();
        } catch (Throwable ignored) {
        }
    }

    public static void closeQuietly(AutoCloseable closeable, Throwable exception) {
        try {
            if (closeable != null)
                closeable.close();
        } catch (Throwable e) {
            exception.addSuppressed(e);
        }
    }
}
