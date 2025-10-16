// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.reader;

import org.jackhuang.hmcl.ui.image.apng.PngChunkCode;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngAnimationControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngFrameControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngGamma;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngHeader;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.map.PngChunkMap;

import java.io.IOException;
import java.io.InputStream;

/**
 * While a PngChunkReader does the low-level reading of a PNG file, it delegates <em>processing</em> of
 * the data to a PngChunkProcessor instance. The class is generic about a specific result type because
 * the intention of a chunk processor is to yield some result, which might be a bitmap, a sequence of
 * bitmaps, a map of chunks, etc.
 */
public interface PngChunkProcessor<ResultT> {

    /**
     * Subclasses can further process the header immediately after PngHeader has been read.
     * A subclass might elect to bail out of subsequent processing, e.g. if the image is too big
     * or has an invalid format.
     *
     * @param header of image
     */
    void processHeader(PngHeader header) throws PngException;

    /**
     * Process the PngGamma object parsed from a ``gaMA`` chunk.
     *
     * @param gamma parsed gamma data.
     * @throws PngException
     */
    void processGamma(PngGamma gamma) throws PngException;

    /**
     * Process the raw trNS data from the PNG file.
     * <p>
     * Note that this data is passed raw (but in a complete byte array).
     * It is not parsed into an object because it allows concrete implementations to define
     * their own way to handle the data. And it is not provided as in InputStream because
     * the length of the data is well defined based on the PNG colour type.
     *
     * @param bytes    array to read data from.
     * @param position offset into the array to begin reading data from.
     * @param length   number of bytes to read from the array.
     * @throws PngException
     */
    void processTransparency(byte[] bytes, int position, int length) throws PngException;

    /**
     * A PLTE chunk has been read and needs to be processed.
     * <p>
     * A byte array is provided to process. A pre-defined palette class is not loaded because
     * different rendering targets may elect to load the palette in different ways.
     * For example, rendering to an ARGB palette may load the RGB data and organise as
     * an array of 32-bit integer ARGB values.
     * </p>
     *
     * @param bytes    representing the loaded palette data. Each colour is represented by three
     *                 bytes, 1 each for red, green and blue.
     * @param position that the colour values begin in bytes
     * @param length   number of bytes that the palette bytes continue. Guaranteed to be a
     *                 multiple of 3.
     * @throws PngException
     */
    void processPalette(byte[] bytes, int position, int length) throws PngException;

    /**
     * Process one IDAT chunk the "default image" bitmap in a given file.
     * <p>
     * The "default", "main" or "primary image" of a file is what you'd generally
     * think of as "the" image in file, namely the image constructed of IDAT chunks
     * and displayed by any normal viewer or browser.
     * <p>
     * I theorise (without proof) that most PNG files in the wild have a single
     * IDAT chunk representing all pixels in the main image, but the PNG specification
     * requires loaders to handle the case where there are multiple IDAT chunks.
     * In the case of multiple chunks the data continues precisely where the last
     * IDAT chunk left off. The same applies to fdAT chunks.
     * <p>
     * The data is provided not in any pre-parsed object or even as a byte array because
     * the bytes can be arbitrarily long. The InputStream provided is a slice of the
     * containing source and will terminate at the end of the IDAT chunk.
     *
     * @param inputStream data source containing all bytes in the image data chunk.
     * @param code        of the chunk which should always be IDAT in this case.
     *                    Compare to this.processFrameImageData.
     * @param position    absolute position within the file. hmmm may be removed
     * @param length      length of bytes of the hunk. hmmm may be removed
     * @throws IOException
     * @throws PngException
     */
    void processDefaultImageData(InputStream inputStream, PngChunkCode code, int position, int length) throws IOException, PngException;

    void processAnimationControl(PngAnimationControl animationControl) throws PngException;

    void processFrameControl(PngFrameControl frameControl) throws PngException;

    /**
     * The reader has determined that bitmap data needs to be processed for an animation frame.
     * The image data may be from an IDAT chunk or an fdAT chunk.
     * <p>
     * Whether the "default image" in a PNG file is to be part of the animation or discarded
     * is determed by the placement of the first fcTL chunk in the file. See the APNG specification.
     *
     * @param inputStream data source containing all bytes in the frame image data chunk.
     * @param code        either IDAT or fdAT. Designed to allow an implementation to detect whether the
     *                    frame is from the "main image" or a subsequent image. May not be necessary.
     * @param position    absolute position within the file. hmmm may be removed
     * @param length      length of bytes of the hunk. hmmm may be removed
     * @throws IOException
     * @throws PngException
     */
    void processFrameImageData(InputStream inputStream, PngChunkCode code, int position, int length) throws IOException, PngException;

    /**
     * A subclass can record data about the chunk here.
     * <p>
     * WARNING: this API may be removed. I'm not sure if it is useful.
     *
     * @param chunkMapItem represents a "map" of a single chunk in the file.
     * @throws PngException
     */
    void processChunkMapItem(PngChunkMap chunkMapItem) throws PngException;

    /**
     * @return the result of processing all chunks. Only ever called after the file is fully read.
     */
    ResultT getResult();
}
