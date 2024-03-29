package org.jackhuang.hmcl.util.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Glavo
 */
public final class ByteArrayBuilder extends ByteArrayOutputStream {

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    public static ByteArrayBuilder createFor(URLConnection connection) {
        long length = connection.getContentLengthLong();

        if (length < 0)
            return new ByteArrayBuilder();
        else if (length >= MAX_ARRAY_SIZE)
            return new ByteArrayBuilder(MAX_ARRAY_SIZE);
        else
            return new ByteArrayBuilder((int) length, true);
    }

    public static ByteArrayBuilder createFor(InputStream inputStream) throws IOException {
        int available = inputStream.available();
        return available > 0 ? new ByteArrayBuilder(available, true) : new ByteArrayBuilder();
    }

    private final boolean knownSize;

    public ByteArrayBuilder() {
        this(256, false);
    }

    public ByteArrayBuilder(int size) {
        this(size, false);
    }

    public ByteArrayBuilder(int size, boolean knownSize) {
        super(size);
        this.knownSize = knownSize;
    }

    private void prepare(int next) {
        assert next > 0;

        int currentCapacity = buf.length;
        int currentSize = this.count;
        int minCapacity = currentSize + next;
        if (minCapacity < 0 || minCapacity > MAX_ARRAY_SIZE) {
            throw new OutOfMemoryError();
        }

        if (currentCapacity >= minCapacity) {
            return;
        }

        int nextCapacity = currentCapacity < MAX_ARRAY_SIZE / 2 ? Math.max(minCapacity, currentSize * 2) : MAX_ARRAY_SIZE;
        buf = Arrays.copyOf(buf, nextCapacity);
    }

    public byte[] getArray() {
        return this.buf;
    }

    public int read(InputStream input) throws IOException {
        int available = input.available();
        if (available > 0) {
            prepare(available);
        }

        int maxRead = buf.length - count;
        if (maxRead == 0) {
            if (knownSize) {
                int b = input.read();
                if (b < 0) {
                    return -1;
                } else {
                    write(b);
                    return 1;
                }
            } else {
                prepare(1);
                maxRead = buf.length - count;
            }
        }

        int n = input.read(buf, count, maxRead);
        if (n > 0) {
            count += n;
        }
        return n;
    }

    public void copyFrom(InputStream input) throws IOException {
        int available = input.available();
        if (available > 0) {
            prepare(available);
        } else if (count == buf.length) {
            prepare(1);
        }

        while (true) {
            int n = input.read(buf, count, buf.length - count);
            if (n <= 0) {
                break;
            }

            count += n;
            if (count == buf.length) {
                if (available > 0) {
                    int b = input.read();
                    if (b < 0) {
                        break;
                    } else {
                        write(b);
                    }
                } else {
                    prepare(1);
                }
            }
        }
    }

    @Override
    public byte[] toByteArray() {
        if (buf.length == count) {
            return buf;
        } else {
            return Arrays.copyOf(buf, count);
        }
    }

    public InputStream toInputStream() {
        return new ByteArrayInputStream(this.buf, 0, this.count);
    }

    @SuppressWarnings("Since15")
    public String toString(Charset charset) {
        return new String(buf, 0, count, charset);
    }

    @Override
    public String toString() {
        return new String(buf, 0, count, UTF_8);
    }
}
