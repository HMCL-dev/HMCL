package org.jackhuang.hmcl.util.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * @author Glavo
 */
public final class ByteArrayOutputBuffer extends ByteArrayOutputStream {

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    public static int getRecommendedSize(long size) {
        if (size < 0)
            return IOUtils.DEFAULT_BUFFER_SIZE;
        else if (size >= MAX_ARRAY_SIZE)
            return MAX_ARRAY_SIZE;
        else
            return Math.max(32, (int) size);
    }

    public ByteArrayOutputBuffer() {
        this(IOUtils.DEFAULT_BUFFER_SIZE);
    }

    public ByteArrayOutputBuffer(int size) {
        super(size);
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

    public byte[] getBuffer() {
        return this.buf;
    }

    public int read(InputStream input) throws IOException {
        int available = input.available();
        if (available > 0) {
            prepare(available);
        }

        int maxRead = buf.length - count;
        if (maxRead == 0) {
            int b = input.read();
            if (b < 0) {
                return -1;
            } else {
                write(b);
                return 1;
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

    public byte[] toByteArrayWithoutCopy() {
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
}
