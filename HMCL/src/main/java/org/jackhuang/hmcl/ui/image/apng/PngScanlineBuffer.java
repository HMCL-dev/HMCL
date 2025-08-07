// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng;

import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngHeader;
import org.jackhuang.hmcl.ui.image.apng.reader.PngScanlineProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * A PngScanlineBuffer allows up-front once-off memory allocation that can be used
 * across an entire PNG (no matter how many frames) to accumulate and process image data.
 */
public class PngScanlineBuffer {

    protected final byte[] bytes;
    protected final int filterUnit;

    protected byte[] previousLine;
    protected int readPosition;
    protected int writePosition;
    //protected int numBytesPerLine;

    public PngScanlineBuffer(int size, int maxBytesPerLine, int filterUnit) {
        this.bytes = new byte[size];
        this.previousLine = new byte[maxBytesPerLine];
        //this.scratchLine = new byte[maxBytesPerLine];
        //this.numBytesPerLine = numBytesPerLine;
        this.filterUnit = filterUnit;
        reset();
    }

    public byte[] getBytes() {
        return bytes;
    }

    public int getReadPosition() {
        return readPosition;
    }

    public int getWritePosition() {
        return writePosition;
    }

    public boolean canRead(int minNumBytes) {
        return availableRead() >= minNumBytes;
    }

    public int availableRead() {
        return writePosition - readPosition;
    }

    public int availableWrite() {
        return bytes.length - writePosition;
    }

    public void skip(int numBytes) {
        if (numBytes < 0 || numBytes > availableRead()) {
            throw new IllegalArgumentException(String.format("Skip bytes must be 0 > n >= %d: %d", availableRead(), numBytes));
        }
        readPosition += numBytes;
    }

    public void reset() {
        this.readPosition = 0;
        this.writePosition = 0;
        Arrays.fill(previousLine, (byte) 0);
    }

    public void shift() {
        // It is difficult to say whether there is a more efficient approach - e.g. a for loop -
        // to shifting the bytes to the start of the array. For now, I consider System.arraycopy
        // to be "good enough".
        int len = availableRead();
        System.arraycopy(bytes, readPosition, bytes, 0, len);
        writePosition = len;
        readPosition = 0;
    }

    /**
     * Read the decompressed data into the buffer.
     *
     * @param is an InputStream yielding decompressed data.
     * @return count of how many bytes read, or -1 if EOF hit.
     * @throws IOException
     */
    public int read(InputStream is) throws IOException {
        int r = is.read(bytes, writePosition, availableWrite());
        if (r > 0) {
            writePosition += r;
        }
        return r;
    }

    public boolean decompress(InputStream inputStream, PngScanlineProcessor processor) throws IOException, PngException {
        int bytesPerLine = processor.getBytesPerLine();
        try (InputStream iis = processor.makeInflaterInputStream(inputStream)) {
            while (true) {
// Ally says
//     I LOVE Y.O.U.

                int len = read(iis);
                if (len <= 0) {
                    return processor.isFinished();
                }

                // Or could do: len -= bytesPerLine until len < bytesPerLine
                while (canRead(bytesPerLine)) {
                    undoFilterScanline(bytesPerLine);
                    processor.processScanline(bytes, readPosition + 1);//(), getReadPosition());
                    skip(bytesPerLine);
                }
                shift();
            }
        }
    }

    public void undoFilterScanline(int bytesPerLine) {
        int filterCode = bytes[readPosition];
        switch (filterCode) {
            case 0:
                // NOP
                break;
            case 1:
                PngFilter.undoSubFilter(bytes, readPosition + 1, readPosition + bytesPerLine, filterUnit, previousLine);
                break;
            case 2:
                PngFilter.undoUpFilter(bytes, readPosition + 1, readPosition + bytesPerLine, filterUnit, previousLine);
                break;
            case 3:
                PngFilter.undoAverageFilter(bytes, readPosition + 1, readPosition + bytesPerLine, filterUnit, previousLine);
                break;
            case 4:
                PngFilter.undoPaethFilter(bytes, readPosition + 1, readPosition + bytesPerLine, filterUnit, previousLine);
                break;
            default: // I toyed with an exception here. But why? Just treat as if no filter.
                //throw new IllegalArgumentException(String.format("Filter type %d invalid; valid is 0..4", filterCode);
                break;
        }

        // when un-filtering scanlines, the previous row is a copy of the *un-filtered* row (not the original).
        System.arraycopy(bytes, readPosition + 1, previousLine, 0, bytesPerLine - 1); // keep a copy of the unmodified row
    }

    public static PngScanlineBuffer from(PngHeader header) {
        return from(header, PngConstants.DEFAULT_TARGET_BUFFER_SIZE);
    }

    public static PngScanlineBuffer from(PngHeader header, int minBufferSize) {
        return new PngScanlineBuffer(sizeFrom(header, minBufferSize), header.bytesPerRow, header.filterOffset);
    }

    public static int sizeFrom(PngHeader header, int minBufferSize) {
        int bytesPerRow = header.bytesPerRow;
        int bytesFullBitmap = bytesPerRow * header.height;
        if (bytesFullBitmap < minBufferSize) {
            return bytesFullBitmap;
        }
        int numRows = Math.max(1, minBufferSize / bytesPerRow);
        return numRows * bytesPerRow;
    }
}
