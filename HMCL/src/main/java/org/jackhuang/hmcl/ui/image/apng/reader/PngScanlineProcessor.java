// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.reader;

import java.io.FilterInputStream;
import java.io.InputStream;

/**
 * A PngScanlineProcessor receives decompressed, de-filtered scanlines, one by one, in order
 * from top to bottom. Interlaced scanlines are not yet supported. The scanline data is not
 * parsed or expanded in any form, so if the PNG is 1-bit monochrome or 16 bit RGBA, the bytes
 * are provided as-is. The job of the processor is to read and reformat each pixel as
 * appropriate for the destination bitmap.
 * <p>
 * Note that a single instance of a PngScanlineProcessor is intended to process a single
 * bitmap and that, when the bitmap is complete, the isFinished() method will return true.
 * In the case of an animated PNG, it is expected that a new PngScanlineProcessor will be
 * created to service the new bitmap.
 * <p>
 * Note: I wonder if this would be better named PngScanlineTransformer because its primary
 * purpose is to convert pixels from raw file format to destination format.
 */
public interface PngScanlineProcessor {

    /**
     * A PngScanlineProcessor is responsible for decompressing the raw (compressed) data.
     * This is important because there can be more than one IDAT and fdAT chunks for a single
     * image, and decompression must occur seamlessly across those chunks, so the decompression
     * must be stateful across multiple invocations of makeInflaterInputStream. In practice, it
     * seems to be rare that there are multiple image data chunks for a single image.
     *
     * @param inputStream stream over raw compressed PNG image data.
     * @return an InflaterInputStream that decompresses the current stream.
     */
    FilterInputStream makeInflaterInputStream(InputStream inputStream);

    /**
     * The processScanline method is invoked when the raw image data has been first decompressed
     * then de-filtered. The PngScanlineProcessor then must interpret each byte according to the
     * specific image format and render it to the destination as appropriate.
     *
     * @param bytes    decompressed, de-filtered bytes, ready for processing.
     * @param position the position that scanline pixels begin in the array. Note that it is
     *                 the responsibility of the PngScanlineProcessor to know exactly how many
     *                 bytes are in the row. This is because a single processor might process
     *                 different frames in an APNG, and each frame can have a different width
     *                 (up to the maximum set in the PNG header).
     */
    void processScanline(byte[] bytes, int position);

    int getBytesPerLine();

    void processTransparentGreyscale(byte k1, byte k0);

    void processTransparentTruecolour(byte r1, byte r0, byte g1, byte g0, byte b1, byte b0);

    /**
     * A PngScanlineProcessor must be able to decompress more than one consecutive IDAT or fdAT
     * chunks. This is because the PNG specification states that a valid PNG file can have more
     * than one consecutive IDAT (and by extension, fdAT) chunks and the data therein must be
     * treated as if concatenated.
     *
     * @return true when the data for the current bitmap is complete.
     */
    boolean isFinished();

    //InflaterInputStream connect(InputStream inputStream);
}
