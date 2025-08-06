// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng;

/**
 * One four-letter (32-byte) code identifying the type of a PNG chunk.
 * <p>
 * Common chunk codes are defined as constants that can be used in a switch statement.
 * Users can add their own chunk codes separately.
 */
public class PngChunkCode {
    public static final PngChunkCode IHDR = new PngChunkCode(PngConstants.IHDR_VALUE, "IHDR");
    public static final PngChunkCode PLTE = new PngChunkCode(PngConstants.PLTE_VALUE, "PLTE");
    public static final PngChunkCode IDAT = new PngChunkCode(PngConstants.IDAT_VALUE, "IDAT");
    public static final PngChunkCode IEND = new PngChunkCode(PngConstants.IEND_VALUE, "IEND");
    public static final PngChunkCode gAMA = new PngChunkCode(PngConstants.gAMA_VALUE, "gAMA");
    public static final PngChunkCode bKGD = new PngChunkCode(PngConstants.bKGD_VALUE, "bKGD");
    public static final PngChunkCode tRNS = new PngChunkCode(PngConstants.tRNS_VALUE, "tRNS");
    public static final PngChunkCode acTL = new PngChunkCode(PngConstants.acTL_VALUE, "acTL");
    public static final PngChunkCode fcTL = new PngChunkCode(PngConstants.fcTL_VALUE, "fcTL");
    public static final PngChunkCode fdAT = new PngChunkCode(PngConstants.fdAT_VALUE, "fdAT");

    public final int numeric;
    public final String letters;

    PngChunkCode(int numeric, String letters) {
        this.numeric = numeric;
        this.letters = letters;
    }

    /**
     * Find out if the chunk is "critical" as defined by http://www.w3.org/TR/PNG/#5Chunk-naming-conventions
     *
     * <pre>
     *     cHNk  <-- 32 bit chunk type represented in text form
     *     ||||
     *     |||+- Safe-to-copy bit is 1 (lower case letter; bit 5 is 1)
     *     ||+-- Reserved bit is 0     (upper case letter; bit 5 is 0)
     *     |+--- Private bit is 0      (upper case letter; bit 5 is 0)
     *     +---- Ancillary bit is 1    (lower case letter; bit 5 is 1)
     * </pre>
     *
     * @return true if this chunk is considered critical according to the PNG spec.
     */
    public boolean isCritical() {
        // Ancillary bit: bit 5 o first byte
        return (numeric & PngConstants.BIT_CHUNK_IS_ANCILLARY) == 0;
    }

    public boolean isAncillary() {
        // Ancillary bit: bit 5 of first byte
        return (numeric & PngConstants.BIT_CHUNK_IS_ANCILLARY) > 0;
    }

    public boolean isPrivate() {
        return (numeric & PngConstants.BIT_CHUNK_IS_PRIVATE) > 0;
    }

    public boolean isPublic() {
        return (numeric & PngConstants.BIT_CHUNK_IS_PRIVATE) == 0;
    }

    public boolean isReserved() {
        return (numeric & PngConstants.BIT_CHUNK_IS_RESERVED) > 0;
    }

    public boolean isSafeToCopy() {
        return (numeric & PngConstants.BIT_CHUNK_IS_SAFE_TO_COPY) > 0;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PngChunkCode that = (PngChunkCode) o;

        if (numeric != that.numeric) return false;
        return !(letters != null ? !letters.equals(that.letters) : that.letters != null);
    }

    @Override
    public int hashCode() {
        int result = numeric;
        result = 31 * result + (letters != null ? letters.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return letters + "(" + numeric + ")";
    }

    public static PngChunkCode from(int code) {
        switch (code) {
            case PngConstants.IHDR_VALUE:
                return IHDR;
            case PngConstants.gAMA_VALUE:
                return gAMA;
            case PngConstants.bKGD_VALUE:
                return bKGD;
            case PngConstants.tRNS_VALUE:
                return tRNS;
            case PngConstants.IDAT_VALUE:
                return IDAT;
            case PngConstants.IEND_VALUE:
                return IEND;
            case PngConstants.acTL_VALUE:
                return acTL;
            case PngConstants.fdAT_VALUE:
                return fdAT;
            default:
                byte[] s = new byte[4];
                s[0] = (byte) ((code & 0xff000000) >> 24);
                s[1] = (byte) ((code & 0x00ff0000) >> 16);
                s[2] = (byte) ((code & 0x0000ff00) >> 8);
                s[3] = (byte) ((code & 0x000000ff));
                return new PngChunkCode(code, new String(s));
        }
    }
}
