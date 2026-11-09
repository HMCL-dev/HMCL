// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.argb8888;

import org.jackhuang.hmcl.ui.image.apng.PngScanlineBuffer;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngAnimationControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngFrameControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngHeader;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;

/**
 * Argb8888BitmapSequenceDirector instances direct an Argb8888Processor to build all frames
 * of an animation into an Argb8888BitmapSequence object.
 */
public class Argb8888BitmapSequenceDirector extends BasicArgb8888Director<Argb8888BitmapSequence> {
    Argb8888BitmapSequence bitmapSequence = null;
    PngFrameControl currentFrame = null;
    private PngHeader header;

    @Override
    public void receiveHeader(PngHeader header, PngScanlineBuffer buffer) throws PngException {
        this.header = header;
        this.bitmapSequence = new Argb8888BitmapSequence(header);
        //this.header = header;
        //defaultImage = new Argb8888Bitmap(header.width, header.height);
        this.scanlineProcessor = Argb8888Processors.from(header, buffer, this.bitmapSequence.defaultImage);
    }

    @Override
    public boolean wantDefaultImage() {
        return true;
    }

    @Override
    public boolean wantAnimationFrames() {
        return true;
    }

    @Override
    public Argb8888ScanlineProcessor beforeDefaultImage() {
        return scanlineProcessor;
    }

    @Override
    public void receiveDefaultImage(Argb8888Bitmap bitmap) {
        this.bitmapSequence.receiveDefaultImage(bitmap);
    }

    @Override
    public void receiveAnimationControl(PngAnimationControl control) {
        this.bitmapSequence.receiveAnimationControl(control);
    }

    @Override
    public Argb8888ScanlineProcessor receiveFrameControl(PngFrameControl control) {
        //throw new IllegalStateException("TODO up to here");
        //return null;
        currentFrame = control;

        //System.out.println("Frame: "+control);

        return scanlineProcessor.cloneWithNewBitmap(header.adjustFor(control)); // TODO: is this going to be a problem?
    }

    @Override
    public void receiveFrameImage(Argb8888Bitmap bitmap) {
        if (null == currentFrame) {
            throw new IllegalStateException("Received a frame image with no frame control in place");
        }
        if (null == bitmapSequence.animationFrames) {
            throw new IllegalStateException("Received a frame image without animation control (or frame list?) in place");
        }
        bitmapSequence.animationFrames.add(new Argb8888BitmapSequence.Frame(currentFrame, bitmap));
        currentFrame = null;
    }

    @Override
    public Argb8888BitmapSequence getResult() {
        return bitmapSequence;
    }
}
