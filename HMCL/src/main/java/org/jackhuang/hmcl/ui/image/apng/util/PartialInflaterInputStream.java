// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

/**
 * A hacked copy of the Java standard java.util.zip.InflaterInputStream that
 * is modified to work with files that have multiple IDAT (or fdAT) chunks for
 * a single bitmap.
 * <p>
 * The default java.util.zip.InflaterInputStream by David Connelly works a treat
 * except that if the java.util.zip.Inflater is expecting data when the stream
 * runs out of data then the stream will die with an exception.
 * <p>
 * The PartialInflaterInputStream, on the other hand, quietly allows the EOF
 * to be returned and leaves the Inflater in the "waiting for more data" data,
 * ready to pick up when the next IDAT (or fdAT) chunk starts.
 * <p>
 * If you're wondering why I didn't just subclass InflaterInputStream, there are
 * a few reasons. The main change is in the ``fill()`` method, which needs to
 * call the method ``ensureOpen()`` but can't because it is private. It is also
 * ideal to return an int but the original ``fill`` method is void. Then there
 * is a change to the ``read()`` method and that references a number of private
 * fields. In short, monkeying around in an attempt to reuse the original seemed
 * more expensive than just copying the original code.
 * <p>
 * The original class documentation states:
 * "This class implements a stream filter for uncompressing data in the
 * "deflate" compression format. It is also used as the basis for other
 * decompression filters, such as GZIPInputStream."
 * </blockquote>
 *
 * @see InflaterInputStream
 */
public class PartialInflaterInputStream extends FilterInputStream {

    /**
     * Decompressor for this stream.
     */
    protected Inflater inf;

    /**
     * Input buffer for decompression.
     */
    protected byte[] buf;

    /**
     * Length of input buffer.
     */
    protected int len;

    private boolean closed = false;
    // this flag is set to true after EOF has reached
    private boolean reachEOF = false;

    /**
     * Check to make sure that this stream has not been closed
     */
    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
    }


    /**
     * Creates a new input stream with the specified decompressor and
     * buffer size.
     *
     * @param in   the input stream
     * @param inf  the decompressor ("inflater")
     * @param size the input buffer size
     * @throws IllegalArgumentException if size is <= 0
     */
    public PartialInflaterInputStream(InputStream in, Inflater inf, int size) {
        super(in);
        if (in == null || inf == null) {
            throw new NullPointerException();
        } else if (size <= 0) {
            throw new IllegalArgumentException("buffer size <= 0");
        }
        this.inf = inf;
        buf = new byte[size];
    }

    /**
     * Creates a new input stream with the specified decompressor and a
     * default buffer size.
     *
     * @param in  the input stream
     * @param inf the decompressor ("inflater")
     */
    public PartialInflaterInputStream(InputStream in, Inflater inf) {
        this(in, inf, 512);
    }

    boolean usesDefaultInflater = false;

    /**
     * Creates a new input stream with a default decompressor and buffer size.
     *
     * @param in the input stream
     */
    public PartialInflaterInputStream(InputStream in) {
        this(in, new Inflater());
        usesDefaultInflater = true;
    }

    private byte[] singleByteBuf = new byte[1];

    /**
     * Reads a byte of uncompressed data. This method will block until
     * enough input is available for decompression.
     *
     * @return the byte read, or -1 if end of compressed input is reached
     * @throws IOException if an I/O error has occurred
     */
    public int read() throws IOException {
        ensureOpen();
        return read(singleByteBuf, 0, 1) == -1 ? -1 : singleByteBuf[0] & 0xff;
    }

    /**
     * Reads uncompressed data into an array of bytes. If <code>len</code> is not
     * zero, the method will block until some input can be decompressed; otherwise,
     * no bytes are read and <code>0</code> is returned.
     *
     * @param b   the buffer into which the data is read
     * @param off the start offset in the destination array <code>b</code>
     * @param len the maximum number of bytes read
     * @return the actual number of bytes read, or -1 if the end of the
     * compressed input is reached or a preset dictionary is needed
     * @throws NullPointerException      If <code>b</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException If <code>off</code> is negative,
     *                                   <code>len</code> is negative, or <code>len</code> is greater than
     *                                   <code>b.length - off</code>
     * @throws ZipException              if a ZIP format error has occurred
     * @throws IOException               if an I/O error has occurred
     */
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }
        try {
            int n;
            while ((n = inf.inflate(b, off, len)) == 0) {
                if (inf.finished() || inf.needsDictionary()) {
                    reachEOF = true;
                    return -1;
                }
                if (inf.needsInput()) {
                    n = fill();
                    if (n <= 0) { // TODO: or only < 0?
                        return -1;
                    }
                }
            }
            return n;
        } catch (DataFormatException e) {
            String s = e.getMessage();
            throw new ZipException(s != null ? s : "Invalid ZLIB data format");
        }
    }

    /**
     * Returns 0 after EOF has been reached, otherwise always return 1.
     * <p>
     * Programs should not count on this method to return the actual number
     * of bytes that could be read without blocking.
     *
     * @return 1 before EOF and 0 after EOF.
     * @throws IOException if an I/O error occurs.
     *
     */
    public int available() throws IOException {
        ensureOpen();
        if (reachEOF) {
            return 0;
        } else {
            return 1;
        }
    }

    private byte[] b = new byte[512];

    /**
     * Skips specified number of bytes of uncompressed data.
     *
     * @param n the number of bytes to skip
     * @return the actual number of bytes skipped.
     * @throws IOException              if an I/O error has occurred
     * @throws IllegalArgumentException if n < 0
     */
    public long skip(long n) throws IOException {
        if (n < 0) {
            throw new IllegalArgumentException("negative skip length");
        }
        ensureOpen();
        int max = (int) Math.min(n, Integer.MAX_VALUE);
        int total = 0;
        while (total < max) {
            int len = max - total;
            if (len > b.length) {
                len = b.length;
            }
            len = read(b, 0, len);
            if (len == -1) {
                reachEOF = true;
                break;
            }
            total += len;
        }
        return total;
    }

    /**
     * Closes this input stream and releases any system resources associated
     * with the stream.
     *
     * @throws IOException if an I/O error has occurred
     */
    public void close() throws IOException {
        if (!closed) {
            if (usesDefaultInflater)
                inf.end();
            in.close();
            closed = true;
        }
    }

    /**
     * Fills input buffer with more data to decompress.
     *
     * @throws IOException if an I/O error has occurred
     */
    protected int fill() throws IOException {
        ensureOpen();
        int n = in.read(buf, 0, buf.length);
        /* A. Ellerton: remove the failure
        if (len == -1) {
            throw new EOFException("Unexpected end of ZLIB input stream");
        }
        inf.setInput(buf, 0, len);
        */
        if (n > 0) {
            len = n;
            inf.setInput(buf, 0, len);
        }
        return n;
    }

    /**
     * Tests if this input stream supports the <code>mark</code> and
     * <code>reset</code> methods. The <code>markSupported</code>
     * method of <code>InflaterInputStream</code> returns
     * <code>false</code>.
     *
     * @return a <code>boolean</code> indicating if this stream type supports
     * the <code>mark</code> and <code>reset</code> methods.
     * @see InputStream#mark(int)
     * @see InputStream#reset()
     */
    public boolean markSupported() {
        return false;
    }

    /**
     * Marks the current position in this input stream.
     *
     * <p> The <code>mark</code> method of <code>InflaterInputStream</code>
     * does nothing.
     *
     * @param readlimit the maximum limit of bytes that can be read before
     *                  the mark position becomes invalid.
     * @see InputStream#reset()
     */
    public void mark(int readlimit) {
    }

    /**
     * Repositions this stream to the position at the time the
     * <code>mark</code> method was last called on this input stream.
     *
     * <p> The method <code>reset</code> for class
     * <code>InflaterInputStream</code> does nothing except throw an
     * <code>IOException</code>.
     *
     * @throws IOException if this method is invoked.
     * @see InputStream#mark(int)
     * @see IOException
     */
    public void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }
}
