// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng;


public class PngConstants {

    public static final int LENGTH_SIGNATURE = 8;
    public static final int LENGTH_fcTL_CHUNK = 4 + 4 + 4 + 4 + 4 + 2 + 2 + 1 + 1;
    public static final int LENGTH_acTL_CHUNK = 4 + 4;

    public static final byte[] BYTES_SIGNATURE = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
    };
    public static final int IHDR_VALUE = 'I' << 24 | 'H' << 16 | 'D' << 8 | 'R'; // 1229472850;
    public static final int PLTE_VALUE = 'P' << 24 | 'L' << 16 | 'T' << 8 | 'E';
    public static final int IDAT_VALUE = 'I' << 24 | 'D' << 16 | 'A' << 8 | 'T'; // 1229209940;
    public static final int IEND_VALUE = 'I' << 24 | 'E' << 16 | 'N' << 8 | 'D'; // 1229278788;
    public static final int gAMA_VALUE = 'g' << 24 | 'A' << 16 | 'M' << 8 | 'A'; // 1732332865;
    public static final int bKGD_VALUE = 'b' << 24 | 'K' << 16 | 'G' << 8 | 'D'; // 1649100612;
    public static final int tRNS_VALUE = 't' << 24 | 'R' << 16 | 'N' << 8 | 'S';
    public static final int acTL_VALUE = 'a' << 24 | 'c' << 16 | 'T' << 8 | 'L';
    public static final int fcTL_VALUE = 'f' << 24 | 'c' << 16 | 'T' << 8 | 'L';
    public static final int fdAT_VALUE = 'f' << 24 | 'd' << 16 | 'A' << 8 | 'T';

    public static final int ERROR_NOT_PNG = 1;
    public static final int ERROR_EOF = 2;
    public static final int ERROR_EOF_EXPECTED = 3;
    public static final int ERROR_UNKNOWN_IO_FAILURE = 4;
    public static final int ERROR_FEATURE_NOT_SUPPORTED = 5;
    public static final int ERROR_INTEGRITY = 6;

    public static final int ONE_K = 1 << 10;
    public static final int DEFAULT_TARGET_BUFFER_SIZE = 32 * ONE_K;

    public static final byte[] GREY_PALETTE_16 = new byte[]{
            (byte) 0x00, //  0
            (byte) 0x11, //  1
            (byte) 0x22, //  2
            (byte) 0x33, //  3
            (byte) 0x44, //  4
            (byte) 0x55, //  5
            (byte) 0x66, //  6
            (byte) 0x77, //  7
            (byte) 0x88, //  8
            (byte) 0x99, //  9
            (byte) 0xaa, // 10
            (byte) 0xbb, // 11
            (byte) 0xcc, // 12
            (byte) 0xdd, // 13
            (byte) 0xee, // 14
            (byte) 0xff, // 15
    };

//    public static final byte[] MASKS_1 = new byte[] {
//            (byte)0x01, // bit 0
//            (byte)0x02, // bit 1
//            (byte)0x04, // bit 2
//            (byte)0x08, // bit 3
//            (byte)0x10, // bit 4
//            (byte)0x20, // bit 5
//            (byte)0x40, // bit 6
//            (byte)0x80, // bit 7
//    };
//
//    public static final byte[] MASKS_2 = new byte[] {
//            (byte)0x03, // bit 0-1
//            (byte)0x0C, // bit 2-3
//            (byte)0x30, // bit 4-5
//            (byte)0xC0, // bit 6-7
//    };
//
//    public static final byte[] MASKS_4 = new byte[] {
//            (byte)0x0F, // bit 0-3
//            (byte)0xF0, // bit 4-7
//    };

    public static final byte[] SHIFTS_1 = new byte[]{
            (byte) 0, // bit 0
            (byte) 1, // bit 1
            (byte) 2, // bit 2
            (byte) 3, // bit 3
            (byte) 4, // bit 4
            (byte) 5, // bit 5
            (byte) 6, // bit 6
            (byte) 7, // bit 7
    };

    public static final byte[] SHIFTS_2 = new byte[]{
            (byte) 0, // bit 0-1
            (byte) 2, // bit 2-3
            (byte) 4, // bit 4-5
            (byte) 6 // bit 6-7
    };

    public static final byte[] SHIFTS_4 = new byte[]{
            (byte) 0x00, // bit 0-3
            (byte) 0x04, // bit 4-7
    };

    public static final int BIT_CHUNK_IS_ANCILLARY = 0x20000000;
    public static final int BIT_CHUNK_IS_PRIVATE = 0x00200000;
    public static final int BIT_CHUNK_IS_RESERVED = 0x00002000;
    public static final int BIT_CHUNK_IS_SAFE_TO_COPY = 0x00000020;


//    public static final byte[] SHIFTS_1 = new byte[] {
//            (byte)0x01, // bit 0
//            (byte)0x02, // bit 1
//            (byte)0x04, // bit 2
//            (byte)0x08, // bit 3
//            (byte)0x10, // bit 4
//            (byte)0x20, // bit 5
//            (byte)0x40, // bit 6
//            (byte)0x80, // bit 7
//    };

    //public static final PngChunkCodes Foo = new PngChunkCodes(123, "Foo");
}
