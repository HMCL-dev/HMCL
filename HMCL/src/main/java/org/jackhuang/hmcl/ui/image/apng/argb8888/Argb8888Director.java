// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.argb8888;

import org.jackhuang.hmcl.ui.image.apng.PngScanlineBuffer;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngAnimationControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngFrameControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngHeader;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;

/**
 * Argb8888Director implementations "direct" a given Argb8888Processor how to
 * control the output. This allows the Argb8888Processor to transform pixels into
 * ARGB8888 format while allowing for radically different final output objects,
 * e.g. a single bitmap, a sequence of bitamps, an Android View or Drawable, etc.
 * <p>
 * TODO: not sure if this will stay in this form. Needs refinement.
 */
public interface Argb8888Director<ResultT> {

    void receiveHeader(PngHeader header, PngScanlineBuffer buffer) throws PngException;

    void receivePalette(Argb8888Palette palette);

    void processTransparentPalette(byte[] bytes, int position, int length) throws PngException;

    void processTransparentGreyscale(byte k1, byte k0) throws PngException;

    void processTransparentTruecolour(byte r1, byte r0, byte g1, byte g0, byte b1, byte b0) throws PngException;

    boolean wantDefaultImage();

    boolean wantAnimationFrames();

    Argb8888ScanlineProcessor beforeDefaultImage();

    void receiveDefaultImage(Argb8888Bitmap bitmap);

    void receiveAnimationControl(PngAnimationControl control);

    Argb8888ScanlineProcessor receiveFrameControl(PngFrameControl control);

    void receiveFrameImage(Argb8888Bitmap bitmap);

    ResultT getResult();
}
