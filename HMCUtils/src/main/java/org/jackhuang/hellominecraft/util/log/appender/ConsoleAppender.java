/*
 * Hello Minecraft!.
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
package org.jackhuang.hellominecraft.util.log.appender;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import org.jackhuang.hellominecraft.util.log.layout.ILayout;

/**
 *
 * @author huangyuhui
 */
public class ConsoleAppender extends OutputStreamAppender {

    public ConsoleAppender(String name, ILayout<? extends Serializable> layout, boolean ignoreExceptions, OutputStream stream, boolean immediateFlush) {
        super(name, layout, ignoreExceptions, stream, true);
    }

    public static class SystemOutStream extends OutputStream {

        @Override
        public void close() {
        }

        @Override
        public void flush() {
            System.out.flush();
        }

        @Override
        public void write(byte[] b) throws IOException {
            System.out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len)
            throws IOException {
            System.out.write(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            System.out.write(b);
        }
    }

    public static class SystemErrStream extends OutputStream {

        @Override
        public void close() {
        }

        @Override
        public void flush() {
            System.err.flush();
        }

        @Override
        public void write(byte[] b) throws IOException {
            System.err.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len)
            throws IOException {
            System.err.write(b, off, len);
        }

        @Override
        public void write(int b) {
            System.err.write(b);
        }
    }
}
