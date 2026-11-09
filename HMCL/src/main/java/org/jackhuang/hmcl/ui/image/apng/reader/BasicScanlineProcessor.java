// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.reader;

import org.jackhuang.hmcl.ui.image.apng.util.PartialInflaterInputStream;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.util.zip.Inflater;

/**
 * A BasicScanlineProcessor manages a re-entrant java.util.zip.Inflater object and
 * basic bytesPerLine management.
 */
public abstract class BasicScanlineProcessor implements PngScanlineProcessor {
    protected final int bytesPerLine;
    protected Inflater inflater = new Inflater();
    //protected PngHeader header;
    //protected PngScanlineBuffer scanlineReader;
    //protected PngFrameControl currentFrame;

    //public DefaultScanlineProcessor(PngHeader header, PngScanlineBuffer scanlineReader, PngFrameControl currentFrame) {
    public BasicScanlineProcessor(int bytesPerScanline) {
//        this.header = header.adjustFor(currentFrame);
//        this.currentFrame = currentFrame;
//        this.scanlineReader = scanlineReader;
//        this.bytesPerLine = this.header.bytesPerRow;
        this.bytesPerLine = bytesPerScanline;
    }

    //@Override
    //abstract public void processScanline(byte[] bytes, int position);

    @Override
    public FilterInputStream makeInflaterInputStream(InputStream inputStream) {
        return new PartialInflaterInputStream(inputStream, inflater);
    }

    @Override
    public int getBytesPerLine() {
        return bytesPerLine;
    }

    @Override
    public boolean isFinished() {
        return inflater.finished();
    }

//    @Override
//    public InflaterInputStream connect(InputStream inputStream) {
//        multipartStream.add(inputStream);
//        return iis;
//    }

    @Override
    public void processTransparentGreyscale(byte k1, byte k0) {
        // NOP by default
    }

    @Override
    public void processTransparentTruecolour(byte r1, byte r0, byte g1, byte g0, byte b1, byte b0) {
        // NOP by default
    }
}
