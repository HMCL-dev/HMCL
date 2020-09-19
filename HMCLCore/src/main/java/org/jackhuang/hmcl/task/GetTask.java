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
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Logging.LOG;

/**
 *
 * @author huangyuhui
 */
public final class GetTask extends DownloadManager.DownloadTask<String> {

    private final Charset charset;

    public GetTask(URL url) {
        this(url, UTF_8);
    }

    public GetTask(URL url, Charset charset) {
        this(url, charset, 3);
    }

    public GetTask(URL url, Charset charset, int retry) {
        this(Collections.singletonList(url), charset, retry);
    }

    public GetTask(List<URL> url) {
        this(url, UTF_8, 3);
    }

    public GetTask(List<URL> urls, Charset charset, int retry) {
        this(new DownloadManager.DownloadTaskStateBuilder()
                .setUrls(urls).setRetry(retry).build(), charset);
    }

    public GetTask(DownloadManager.DownloadTaskState state) {
        this(state, UTF_8);
    }

    public GetTask(DownloadManager.DownloadTaskState state, Charset charset) {
        super(state);
        this.charset = charset;

        setName(state.getFirstUrl().toString());
    }

    static class ByteArrayList {
        protected byte[] buf;

        protected int count;

        public ByteArrayList() {
            this(32);
        }

        public ByteArrayList(int size) {
            if (size < 0) {
                throw new IllegalArgumentException("Negative initial size: "
                        + size);
            }
            buf = new byte[size];
        }

        void ensureCapacity(int minCapacity) {
            // overflow-conscious code
            if (minCapacity - buf.length > 0)
                grow(minCapacity);
        }

        private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

        private void grow(int minCapacity) {
            // overflow-conscious code
            int oldCapacity = buf.length;
            int newCapacity = oldCapacity << 1;
            if (newCapacity - minCapacity < 0)
                newCapacity = minCapacity;
            if (newCapacity - MAX_ARRAY_SIZE > 0)
                newCapacity = hugeCapacity(minCapacity);
            buf = Arrays.copyOf(buf, newCapacity);
        }

        private static int hugeCapacity(int minCapacity) {
            if (minCapacity < 0) // overflow
                throw new OutOfMemoryError();
            return (minCapacity > MAX_ARRAY_SIZE) ?
                    Integer.MAX_VALUE :
                    MAX_ARRAY_SIZE;
        }

        public synchronized void write(int b) {
            ensureCapacity(count + 1);
            buf[count] = (byte) b;
            count += 1;
        }

        public synchronized void write(byte b[], int off, int len) {
            if (off < 0 || len < 0 || off + len > b.length) {
                throw new IllegalArgumentException("write " + b.length + ", off=" + off + ", len=" + len);
            }
            ensureCapacity(count + len);
            System.arraycopy(b, off, buf, count, len);
            count += len;
        }

        public synchronized void write(int pos, byte b[], int off, int len) {
            ensureCapacity(pos + len);
            System.arraycopy(b, off, buf, pos, len);
            count = Math.max(count, pos + len);
        }

        public synchronized void reset() {
            count = 0;
        }

        public synchronized byte[] toByteArray() {
            return Arrays.copyOf(buf, count);
        }

        public synchronized int size() {
            return count;
        }

        public synchronized String toString() {
            return new String(buf, 0, count);
        }

        public synchronized String toString(String charsetName)
                throws UnsupportedEncodingException
        {
            return new String(buf, 0, count, charsetName);
        }

        public synchronized String toString(Charset charset) {
            return new String(buf, 0, count, charset);
        }
    }

    @Override
    protected DownloadManager.Downloader<String> createDownloader() {
        return new DownloadManager.Downloader<String>() {
            private ByteArrayList bytes;

            @Override
            protected EnumCheckETag shouldCheckETag() {
                return EnumCheckETag.CHECK_E_TAG;
            }

            @Override
            protected String finishWithCachedResult(Path cachedFile) throws IOException {
                return FileUtils.readText(cachedFile);
            }

            @Override
            protected void write(long pos, byte[] buffer, int offset, int len) {
                if (pos > Integer.MAX_VALUE/* check overflow */ || pos + len > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("pos too large");
                }
                bytes.ensureCapacity((int)pos + len);
                bytes.write((int)pos, buffer, offset, len);
            }

            @Override
            protected void onStart() {
                bytes = new ByteArrayList();
            }

            @Override
            protected void onContentLengthChanged(long contentLength) {
                if (contentLength > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Content length too large");
                }
                bytes.ensureCapacity((int)contentLength);
            }

            @Override
            public String finish() throws IOException {
                if (!state.isFinished()) return null;

                String result = bytes.toString(charset.name());

                if (getCheckETag() == EnumCheckETag.CHECK_E_TAG) {
                    try {
                        repository.cacheText(result, state.getSegments().get(0).getConnection());
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "Failed to cache text", e);
                    }
                }

                return result;
            }
        };
    }

}
