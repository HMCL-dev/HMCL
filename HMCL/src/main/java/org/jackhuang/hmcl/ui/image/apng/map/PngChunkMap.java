// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.map;

import org.jackhuang.hmcl.ui.image.apng.PngChunkCode;

/**
 * A single chunk from a PNG file is represented by a PngChunkMap.
 * <p>
 * WARNING: not sure if this API will remain.
 * </p>
 */
public class PngChunkMap {
    /**
     * The code like IHDR, IPAL, IDAT. If the chunk code is non-standard,
     * the UNKNOWN code will be used and the codeString will be set.
     */
    public PngChunkCode code;

    /**
     * Number of bytes containing the data portion of the chunk. Note that this excludes
     * the last 4 bytes that are the CRC checksom.
     */
    public int dataLength;

    /**
     * Integer offset of the first byte of data in this chunk (the byte immediately
     * following the chunk length 32-bits and the chunk type 32-bits) from byte zero
     * of the source.
     */
    public int dataPosition;

    public int checksum;

    public PngChunkMap(PngChunkCode code, int dataPosition, int dataLength, int checksum) {
        this.code = code;
        this.dataLength = dataLength;
        this.dataPosition = dataPosition;
        this.checksum = checksum;
    }

    @Override
    public String toString() {
        return "PngChunkMap{" +
                "letters=" + code +
                ", dataLength=" + dataLength +
                ", dataPosition=" + dataPosition +
                ", checksum=" + checksum +
                '}';
    }
}
