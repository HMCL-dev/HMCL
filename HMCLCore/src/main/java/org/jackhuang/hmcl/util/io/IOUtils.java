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

import java.io.*;
import java.nio.charset.Charset;
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

    public static final int DEFAULT_BUFFER_SIZE = 32 * 1024;

    public static byte[] readFully(InputStream stream) throws IOException {
        try (stream) {
            return stream.readAllBytes();
        }
    }

    public static String readFullyAsString(InputStream stream) throws IOException {
        return new String(readFully(stream), UTF_8);
    }

    public static String readFullyAsString(InputStream stream, Charset charset) throws IOException {
        return new String(readFully(stream), charset);
    }

    public static void skipNBytes(InputStream input, long n) throws IOException {
        while (n > 0) {
            long ns = input.skip(n);
            if (ns > 0 && ns <= n)
                n -= ns;
            else if (ns == 0) {
                if (input.read() == -1)
                    throw new EOFException();
                n--;
            } else {
                throw new IOException("Unexpected skip bytes. Expected: " + n + ", Actual: " + ns);
            }
        }
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
