// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.reader;

import org.jackhuang.hmcl.ui.image.apng.PngAnimationType;
import org.jackhuang.hmcl.ui.image.apng.PngChunkCode;
import org.jackhuang.hmcl.ui.image.apng.PngConstants;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngAnimationControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngFrameControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngGamma;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngHeader;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.error.PngIntegrityException;
import org.jackhuang.hmcl.ui.image.apng.map.PngChunkMap;

import java.io.IOException;

/**
 * The DefaultPngChunkReader is the default PNG chunk-reading workhorse.
 * <p>
 * Note that any chunk types not recognised can be processed in the readOtherChunk()
 * method.
 */
public class DefaultPngChunkReader<ResultT> implements PngChunkReader<ResultT> {
    protected PngChunkProcessor<ResultT> processor;
    protected boolean seenHeader = false;
    protected int idatCount = 0;
    protected int apngSequenceExpect = 0;
    protected PngAnimationType animationType = PngAnimationType.NOT_ANIMATED;
    //private PngMainImageOp mainImageOp = PngMainImageOp.MAIN_IMAGE_KEEP;

    public DefaultPngChunkReader(PngChunkProcessor<ResultT> processor) {
        this.processor = processor;
    }

    @Override
    public boolean readChunk(PngSource source, int code, int dataLength) throws PngException, IOException {
        int dataPosition = source.tell(); // note the start position before any further reads are done.

        if (dataLength < 0) {
            throw new PngIntegrityException(String.format("Corrupted read (Data length %d)", dataLength));
        }

        switch (code) {
            case PngConstants.IHDR_VALUE:
                readHeaderChunk(source, dataLength);
                break;

            case PngConstants.IEND_VALUE:
                // NOP
                break;

            case PngConstants.gAMA_VALUE:
                readGammaChunk(source, dataLength);
                break;

            case PngConstants.bKGD_VALUE:
                readBackgroundChunk(source, dataLength);
                break;

            case PngConstants.tRNS_VALUE:
                readTransparencyChunk(source, dataLength);
                break;

            case PngConstants.PLTE_VALUE:
                readPaletteChunk(source, dataLength);
                break;

            case PngConstants.IDAT_VALUE:
                readImageDataChunk(source, dataLength);
                break;

            case PngConstants.acTL_VALUE:
                readAnimationControlChunk(source, dataLength);
                break;

            case PngConstants.fcTL_VALUE:
                readFrameControlChunk(source, dataLength);
                break;

            case PngConstants.fdAT_VALUE:
                readFrameImageDataChunk(source, dataLength);
                break;

            default:
                readOtherChunk(code, source, dataPosition, dataLength);
                break;
        }

        int chunkChecksum = source.readInt();
        processChunkEnd(code, dataPosition, dataLength, chunkChecksum);
        return code == PngConstants.IEND_VALUE;
    }

    @Override
    public void processChunkEnd(int code, int dataPosition, int dataLength, int chunkChecksum) throws PngException {
        processor.processChunkMapItem(new PngChunkMap(PngChunkCode.from(code), dataPosition, dataLength, chunkChecksum));
        //container.chunks.add(new PngChunkMap(PngChunkCode.from(code), dataLength, dataPosition, chunkChecksum));
    }

    @Override
    public void readHeaderChunk(PngSource source, int dataLength) throws IOException, PngException {
        PngHeader header = PngHeader.from(source.getDis());
        seenHeader = true;
        processor.processHeader(header); // Hmm. Prefer header = PngHeader.from(source) ?
    }

    @Override
    public void readGammaChunk(PngSource source, int dataLength) throws PngException, IOException {
        processor.processGamma(PngGamma.from(source.getDis()));
    }

    @Override
    public void readTransparencyChunk(PngSource source, int dataLength) throws IOException, PngException {
        // TODO
        //processor.processTransparency(PngTransparency.from(source, dataLength));
        //throw new PngFeatureException("TODO UP TO HERE");
        //source.skip(dataLength);

//        if (dataLength % 3 != 0) {
//            throw new PngIntegrityException(String.format("png spec: palette chunk length must be divisible by 3: %d", dataLength));
//        }

        if (source.supportsByteAccess()) {
            processor.processTransparency(source.getBytes(), source.tell(), dataLength);
            source.skip(dataLength);
        } else {
            byte[] paletteBytes = new byte[dataLength];
            //ByteStreams.readFully(source.getBis(), paletteBytes);
            source.getDis().readFully(paletteBytes);
            processor.processTransparency(paletteBytes, 0, dataLength);
        }
    }

    @Override
    public void readBackgroundChunk(PngSource source, int dataLength) throws IOException, PngException {
        if (!seenHeader) {
            throw new PngIntegrityException("bKGD chunk received before IHDR chunk");
        }
        // TODO
        //processor.processBackground(PngBackground.from(source, dataLength);
        source.skip(dataLength);
    }

    @Override
    public void readPaletteChunk(PngSource source, int dataLength) throws IOException, PngException {

        if (dataLength % 3 != 0) {
            throw new PngIntegrityException(String.format("png spec: palette chunk length must be divisible by 3: %d", dataLength));
        }
        // TODO: can check if colour type matches palette type, or if any palette received before (overkill?)

        if (source.supportsByteAccess()) {
            processor.processPalette(source.getBytes(), source.tell(), dataLength);
            source.skip(dataLength);
        } else {
            byte[] paletteBytes = new byte[dataLength];
            //ByteStreams.readFully(source.getBis(), paletteBytes);
            source.getDis().readFully(paletteBytes);
            processor.processPalette(paletteBytes, 0, dataLength);
        }
    }

    @Override
    public void readImageDataChunk(PngSource source, int dataLength) throws PngException, IOException {

        if (idatCount == 0 && apngSequenceExpect == 0) {
            // Processing a plain PNG (non animated) IDAT chunk
            animationType = PngAnimationType.NOT_ANIMATED; // processor.chooseApngImageType(PngAnimationType.NOT_ANIMATED, null);
        }
        idatCount++;

        switch (animationType) {
            case ANIMATED_DISCARD_DEFAULT_IMAGE:
                // do nothing
                source.skip(dataLength);
                break;
            case ANIMATED_KEEP_DEFAULT_IMAGE:
                processor.processFrameImageData(source.slice(dataLength), PngChunkCode.IDAT, source.tell(), dataLength);
                break;

            case NOT_ANIMATED:
            default:
                processor.processDefaultImageData(source.slice(dataLength), PngChunkCode.IDAT, source.tell(), dataLength);
                break;
        }
//        source.skip(dataLength);
    }

    @Override
    public void readAnimationControlChunk(PngSource source, int dataLength) throws IOException, PngException {
        if (dataLength != PngConstants.LENGTH_acTL_CHUNK) {
            throw new PngIntegrityException(String.format("acTL chunk length must be %d, not %d", PngConstants.LENGTH_acTL_CHUNK, dataLength));
        }
        processor.processAnimationControl(new PngAnimationControl(source.readInt(), source.readInt()));
    }

    @Override
    public void readFrameControlChunk(PngSource source, int dataLength) throws IOException, PngException {
        if (dataLength != PngConstants.LENGTH_fcTL_CHUNK) {
            throw new PngIntegrityException(String.format("fcTL chunk length must be %d, not %d", PngConstants.LENGTH_fcTL_CHUNK, dataLength));
        }
        int sequence = source.readInt(); // TODO: check sequence # is correct or PngIntegrityException

        if (sequence != apngSequenceExpect) {
            throw new PngIntegrityException(String.format("fctl chunk expected sequence %d but received %d", apngSequenceExpect, sequence));
        }
        apngSequenceExpect++; // ready for next time

        PngFrameControl frame = new PngFrameControl(
                sequence,
                source.readInt(), // width
                source.readInt(), // height
                source.readInt(), // x offset
                source.readInt(), // y offset
                source.readUnsignedShort(), // delay numerator
                source.readUnsignedShort(), // delay denominator
                source.readByte(), // dispose op
                source.readByte() // blend op
        );

        if (sequence == 0) { // We're at the first frame...
            if (idatCount == 0) { // Not seen any IDAT chunks yet
                // APNG Spec says that when the first fcTL chunk is received *before* the first IDAT chunk
                // the main image of the PNG becomes the first frame in the animation.

                animationType = PngAnimationType.ANIMATED_KEEP_DEFAULT_IMAGE; //processor.chooseApngImageType(PngAnimationType.ANIMATED_KEEP_DEFAULT_IMAGE, frame);

                //mainImageOp = PngMainImageOp.MAIN_IMAGE_STARTS_ANIMATION;
            } else {
                //mainImageOp = PngMainImageOp.MAIN_IMAGE_DISCARD;
                animationType = PngAnimationType.ANIMATED_DISCARD_DEFAULT_IMAGE; // processor.chooseApngImageType(PngAnimationType.ANIMATED_DISCARD_DEFAULT_IMAGE, frame);
            }
        } else {
            // fall through
        }

        processor.processFrameControl(frame);
    }

    //public abstract void setMainImageOp(PngMainImageOp op);

    @Override
    public void readFrameImageDataChunk(PngSource source, int dataLength) throws IOException, PngException {
        // Note that once the sequence number is confirmed as being correct that there
        // is no need to retain it in subsequent data.
        int position = source.tell();
        int sequence = source.readInt();
        dataLength -= 4; // After reading the sequence number the data is just like IDAT.

        if (sequence != apngSequenceExpect) {
            throw new PngIntegrityException(String.format("fdAT chunk expected sequence %d but received %d", apngSequenceExpect, sequence));
        }
        apngSequenceExpect++; // for next time

        //processFrameData(sequence, source, dataLength);
        //processFrameImageData(source, dataLength);
        processor.processFrameImageData(source.slice(dataLength), PngChunkCode.fdAT, source.tell(), dataLength);

//        PngFrameControl current = container.getCurrentAnimationFrame();
//
//        //imageDecoder
//        // TODO: send image bytes to digester
//        current.appendImageData(new PngChunkMap(PngChunkCode.fdAT, dataLength, position, 0));
//
//        // TODO: skip everything except the frame sequence number


//        source.skip(dataLength);
    }

    @Override
    public void readOtherChunk(int code, PngSource source, int dataPosition, int dataLength) throws IOException {
        // If we're not processing it, got to skip it.
        source.skip(dataLength);
    }

    @Override
    public void finishedChunks(PngSource source) throws PngException, IOException {
    }

    @Override
    public ResultT getResult() {
        return processor.getResult();
    }

    public boolean isSeenHeader() {
        return seenHeader;
    }

    public int getIdatCount() {
        return idatCount;
    }

    public int getApngSequenceExpect() {
        return apngSequenceExpect;
    }

    public PngAnimationType getAnimationType() {
        return animationType;
    }
}
