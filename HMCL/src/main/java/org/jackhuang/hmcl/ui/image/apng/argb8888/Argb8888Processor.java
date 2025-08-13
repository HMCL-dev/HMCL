// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.argb8888;

import org.jackhuang.hmcl.ui.image.apng.PngChunkCode;
import org.jackhuang.hmcl.ui.image.apng.PngScanlineBuffer;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngAnimationControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngFrameControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngGamma;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngHeader;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.error.PngIntegrityException;
import org.jackhuang.hmcl.ui.image.apng.map.PngChunkMap;
import org.jackhuang.hmcl.ui.image.apng.reader.PngChunkProcessor;

import java.io.IOException;
import java.io.InputStream;

/**
 * Concrete implementation of a chunk processor designed to process pixels into
 * 32-bit ARGB8888 format.
 */
public class Argb8888Processor<ResultT> implements PngChunkProcessor<ResultT> { //PngChunkProcessor<Argb8888Bitmap> {

    protected PngHeader header = null;
    protected PngScanlineBuffer scanlineReader = null;
    protected Argb8888Director<ResultT> builder = null;
    protected Argb8888ScanlineProcessor scanlineProcessor = null;


    public Argb8888Processor(Argb8888Director<ResultT> builder) {
        this.builder = builder;
    }

    @Override
    public void processHeader(PngHeader header) throws PngException {
//        super.processHeader(header);
////        if (header.bitDepth != 1 || header.colourType != PngColourType.PNG_GREYSCALE) {
////            throw new PngFeatureException("ARGB888 only supports 1-bit greyscale");
////        }
//        if (header.isGreyscale()) {
//            palette = Argb8888Palette.forGreyscale(header.bitDepth);
//        }
//        scanlineReader = PngScanlineBuffer.from(header);
        this.header = header;
        this.scanlineReader = PngScanlineBuffer.from(header);
        this.builder.receiveHeader(this.header, this.scanlineReader);
    }

    @Override
    public void processGamma(PngGamma gamma) throws PngException {
        // No gamma processing is done at the moment.
    }

    @Override
    public void processPalette(byte[] bytes, int position, int length) throws PngException {
        //palette = Argb8888Palette.fromPaletteBytes(bytes, position, length);
        //scanlineProcessor = Argb8888Processors.fromPalette(this.header, palette);
        builder.receivePalette(Argb8888Palette.fromPaletteBytes(bytes, position, length));
    }

    @Override
    public void processTransparency(byte[] bytes, int position, int length) throws PngException {
        switch (header.colourType) {
            case PNG_GREYSCALE: // colour type 0
                // grey sample value (2 bytes)
                if (length != 2) {
                    throw new PngIntegrityException(String.format("tRNS chunk for greyscale image must be exactly length=2, not %d", length));
                }
                builder.processTransparentGreyscale(bytes[0], bytes[1]);
                break;

            case PNG_TRUECOLOUR: // colour type 2
                // red, green, blue samples, EACH with two bytes (16-bits)
                builder.processTransparentTruecolour(bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5]);
                break;

            case PNG_INDEXED_COLOUR: // colour type 3
                // This is a sequence of one-byte alpha values to apply to each palette entry starting at zero.
                // The number of entries may be less than the size of the palette, but not more.
                builder.processTransparentPalette(bytes, position, length);
                break;

            case PNG_GREYSCALE_WITH_ALPHA:
            case PNG_TRUECOLOUR_WITH_ALPHA:
            default:
                throw new PngIntegrityException("Illegal to have tRNS chunk with image type " + header.colourType.name);
        }
    }

    /**
     * The only supported animation type is not "NOT_ANIMATED".
     */
//    @Override
//    public PngAnimationType chooseApngImageType(PngAnimationType type, PngFrameControl currentFrame) throws PngException {
//        scanlineProcessor = Argb8888ScanlineProcessor.from(header, scanlineReader, currentFrame);
//        return PngAnimationType.NOT_ANIMATED;
//    }
    @Override
    public void processDefaultImageData(InputStream inputStream, PngChunkCode code, int position, int length) throws IOException, PngException {
        if (!builder.wantDefaultImage()) {
            inputStream.skip(length); // important!
            return;
        }

        if (null == scanlineProcessor) { // TODO: is that a good enough metric? Or could be numIdat==0?
            scanlineProcessor = builder.beforeDefaultImage();
            if (null == scanlineProcessor) throw new IllegalStateException("Builder must create scanline processor");
        }

        if (scanlineReader.decompress(inputStream, scanlineProcessor)) {
            // If here, the image is fully decompressed.
            builder.receiveDefaultImage(scanlineProcessor.getBitmap());
            scanlineProcessor = null;
            scanlineReader.reset();
        }
    }

    @Override
    public void processAnimationControl(PngAnimationControl animationControl) throws PngException {
        if (builder.wantAnimationFrames()) {
            builder.receiveAnimationControl(animationControl);
        }
    }

    @Override
    public void processFrameControl(PngFrameControl frameControl) throws PngException {
        if (!builder.wantAnimationFrames()) {
            return;
        }

        if (null == scanlineProcessor) {
            // If here, the data in this frame image has not started
            scanlineProcessor = builder.receiveFrameControl(frameControl);
            if (null == scanlineProcessor)
                throw new IllegalStateException("Builder must create scanline processor for frame");
        } else {
            throw new IllegalStateException("received animation frame control but image data was in progress");
        }
    }

    @Override
    public void processFrameImageData(InputStream inputStream, PngChunkCode code, int position, int length) throws IOException, PngException {
        //throw new PngFeatureException("PngArgb8888Processor does not support animation frames");
        if (!builder.wantAnimationFrames()) {
            inputStream.skip(length);
            return;
        }

        if (null == scanlineProcessor) {
            throw new IllegalStateException("received animation frame image data before frame control or without processor in place");
        }

        if (scanlineReader.decompress(inputStream, scanlineProcessor)) {
            // If here, the image is fully decompressed.
            builder.receiveFrameImage(scanlineProcessor.getBitmap());
            scanlineReader.reset();
            scanlineProcessor = null;
        }
    }

    @Override
    public void processChunkMapItem(PngChunkMap chunkMapItem) throws PngException {
        // NOP
    }

    @Override
    public ResultT getResult() {
        //return scanlineProcessor.getBitmap();
        return builder.getResult();
    }
}
