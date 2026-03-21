package org.jackhuang.hmcl.util.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;

/// [SeekableByteChannel] with read buffer for efficient reading.
///
/// Writing ([SeekableByteChannel#write(java.nio.ByteBuffer)]) are passthrough directly to the underlying channel, and will invalidate the internal data buffer.
///
/// There's no guarantee on thread safety of this implementation.
public class BufferedSeekableByteChannel implements SeekableByteChannel {

    private final SeekableByteChannel underlying;
    private final ByteBuffer buffer;
    /// `buffer[i] == underlying[i + bufferStart]`
    private long bufferStart;
    /// `false` if [#buffer] should be refreshed
    private boolean bufferValid;
    /// current position, relative to the start of file
    private long position;

    /// Create a [BufferedSeekableByteChannel] with a buffer size of 8192
    public BufferedSeekableByteChannel(SeekableByteChannel underlying) {
        this(underlying, 8192);
    }

    /// Create a [BufferedSeekableByteChannel] with specified buffer size
    ///
    /// @throws IllegalArgumentException if `bufferSize <= 0`
    /// @throws NullPointerException if `underlying == null`
    public BufferedSeekableByteChannel(SeekableByteChannel underlying, int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException(String.format("int bufferSize <= 0 (%s <= 0)", bufferSize));
        }
        this.underlying = Objects.requireNonNull(underlying, "SeekableByteChannel underlying == null");
        this.buffer = ByteBuffer.allocate(bufferSize);
        this.bufferStart = 0;
        this.bufferValid = false;
        this.position = 0;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        ensureOpen();
        if (!dst.hasRemaining()) {
            return 0;
        }

        int totalRead = 0;
        while (dst.hasRemaining()) {
            if (!bufferValid || buffer.remaining() == 0) {
                fillBuffer();
                if (!bufferValid || buffer.remaining() == 0) {
                    // EOL
                    break;
                }
            }

            int bytesToCopy = Math.min(buffer.remaining(), dst.remaining());
            ByteBuffer slice = buffer.slice();
            slice.limit(bytesToCopy);
            dst.put(slice);
            buffer.position(buffer.position() + bytesToCopy);
            totalRead += bytesToCopy;
            position += bytesToCopy;
        }

        return totalRead == 0 && !bufferValid ? -1 : totalRead;
    }

    private void fillBuffer() throws IOException {
        underlying.position(position);

        buffer.clear();
        int bytesRead = underlying.read(buffer);
        if (bytesRead > 0) {
            buffer.flip();
            bufferStart = position;
            bufferValid = true;
        } else {
            // EOF
            bufferValid = false;
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        ensureOpen();
        underlying.position(position);

        int written = underlying.write(src);
        if (written > 0) {
            position += written;
            invalidateBuffer();
        }
        return written;
    }

    @Override
    public long position() throws IOException {
        ensureOpen();
        return position;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        ensureOpen();
        if (newPosition < 0) {
            throw new IllegalArgumentException("Negative position");
        }

        // avoid rebuilding buffer if possible
        if (bufferValid && newPosition >= bufferStart && newPosition < bufferStart + buffer.limit()) {
            int bufferOffset = (int) (newPosition - bufferStart);
            buffer.position(bufferOffset);
        } else {
            invalidateBuffer();
        }
        position = newPosition;
        return this;
    }

    @Override
    public long size() throws IOException {
        ensureOpen();
        return underlying.size();
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        ensureOpen();
        underlying.truncate(size);
        invalidateBuffer();
        if (position > size) {
            position = size;
            underlying.position(position);
        }
        return this;
    }

    @Override
    public boolean isOpen() {
        return underlying.isOpen();
    }

    @Override
    public void close() throws IOException {
        underlying.close();
        invalidateBuffer();
    }

    private void invalidateBuffer() {
        bufferValid = false;
        buffer.clear();
    }

    private void ensureOpen() throws ClosedChannelException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
    }
}