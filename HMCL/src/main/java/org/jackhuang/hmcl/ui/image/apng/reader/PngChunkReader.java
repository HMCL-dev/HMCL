// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.reader;

import org.jackhuang.hmcl.ui.image.apng.error.PngException;

import java.io.IOException;

/**
 * Created by aellerton on 15/05/2015.
 */
public interface PngChunkReader<ResultT> extends PngReader<ResultT> {
    @Override
    boolean readChunk(PngSource source, int code, int dataLength) throws PngException, IOException;

    void processChunkEnd(int code, int dataPosition, int dataLength, int chunkChecksum) throws PngException;

    void readHeaderChunk(PngSource source, int dataLength) throws IOException, PngException;

    void readGammaChunk(PngSource source, int dataLength) throws PngException, IOException;

    /**
     * Process the tRNS chunk.
     * <p>
     * From http://www.w3.org/TR/PNG/#11tRNS:
     * <p>
     * The tRNS chunk specifies either alpha values that are associated with palette entries (for indexed-colour
     * images) or a single transparent colour (for greyscale and truecolour images). The tRNS chunk contains:
     * <p>
     * Colour type 0:
     * <p>
     * Grey sample value 2 bytes
     * <p>
     * Colour type 2:
     * <p>
     * Red sample value	2 bytes
     * Blue sample value	2 bytes
     * Green sample value	2 bytes
     * <p>
     * Colour type 3:
     * <p>
     * Alpha for palette index 0	1 byte
     * Alpha for palette index 1	1 byte
     * ...etc...	1 byte
     * <p>
     * Note that for palette colour types the number of transparency entries may be less than the number of
     * entries in the palette. In that case all missing entries are assumed to be fully opaque.
     *
     * @param source
     * @param dataLength
     */
    void readTransparencyChunk(PngSource source, int dataLength) throws IOException, PngException;

    /**
     * Process the bKGD chunk.
     * <p>
     * From http://www.w3.org/TR/PNG/#11bKGD:
     * <p>
     * The bKGD chunk specifies a default background colour to present the image against.
     * If there is any other preferred background, either user-specified or part of a larger
     * page (as in a browser), the bKGD chunk should be ignored. The bKGD chunk contains:
     * <p>
     * Colour types 0 and 4
     * Greyscale	2 bytes
     * Colour types 2 and 6
     * Red	2 bytes
     * Green	2 bytes
     * Blue	2 bytes
     * Colour type 3
     * Palette index	1 byte
     * <p>
     * For colour type 3 (indexed-colour), the value is the palette index of the colour to be used as background.
     * <p>
     * For colour types 0 and 4 (greyscale, greyscale with alpha), the value is the grey level to be used as
     * background in the range 0 to (2bitdepth)-1. For colour types 2 and 6 (truecolour, truecolour with alpha),
     * the values are the colour to be used as background, given as RGB samples in the range 0 to (2bitdepth)-1.
     * In each case, for consistency, two bytes per sample are used regardless of the image bit depth. If the image
     * bit depth is less than 16, the least significant bits are used and the others are 0.
     *
     * @param source
     * @param dataLength
     * @throws IOException
     */
    void readBackgroundChunk(PngSource source, int dataLength) throws IOException, PngException;

    void readPaletteChunk(PngSource source, int dataLength) throws IOException, PngException;

    /**
     * Read the IDAT chunk.
     * <p>
     * The default implementation skips the data, deferring to the finishedChunks() method
     * to process the data. Key reasons to do this:
     * <ul>
     *     <li>There could be multiple IDAT chunks and the streams need to
     *     be concatenated. This could be done in a single pass but it's more
     *     complicated and is not the objective of this implementation.</li>
     *     <li>This might be an APNG file and the IDAT chunk(s) are to be skipped.</li>
     * </ul>
     *
     * @param source     to read from
     * @param dataLength in bytes of the data
     * @throws PngException
     * @throws IOException
     */
    void readImageDataChunk(PngSource source, int dataLength) throws PngException, IOException;

    void readAnimationControlChunk(PngSource source, int dataLength) throws IOException, PngException;

    /**
     * Read the fcTL chunk.
     * <p>
     * See https://wiki.mozilla.org/APNG_Specification#.60fcTL.60:_The_Frame_Control_Chunk
     *
     * @param source
     * @param dataLength
     * @throws IOException
     * @throws PngException
     */
    void readFrameControlChunk(PngSource source, int dataLength) throws IOException, PngException;

    void readFrameImageDataChunk(PngSource source, int dataLength) throws IOException, PngException;

    /**
     * Give subclasses the opportunity to process a chunk code that was not recognised.
     *
     * @param code         chunk type as integer.
     * @param source       of PNG data, positioned to read the data bytes.
     * @param dataPosition offset from absolute start of bytes that data beings.
     * @param dataLength   of this chunk
     * @throws IOException
     */
    void readOtherChunk(int code, PngSource source, int dataPosition, int dataLength) throws IOException;
}
