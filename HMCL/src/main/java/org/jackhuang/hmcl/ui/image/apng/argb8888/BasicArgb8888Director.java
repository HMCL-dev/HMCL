// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.argb8888;

import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.error.PngIntegrityException;

/**
 * Common functionality for Argb8888Director implementations.
 */
public abstract class BasicArgb8888Director<ResultT> implements Argb8888Director<ResultT> {
    protected Argb8888ScanlineProcessor scanlineProcessor;

    @Override
    public void receivePalette(Argb8888Palette palette) {
        scanlineProcessor.setPalette(palette);
    }

    @Override
    public void processTransparentPalette(byte[] bytes, int position, int length) throws PngException {
        Argb8888Palette palette = scanlineProcessor.getPalette();
        if (null == palette) {
            throw new PngIntegrityException("Received tRNS data but no palette is in place");
        }
        if (length <= 0 || length > palette.size()) {
            throw new PngIntegrityException(String.format("Received tRNS data length is invalid. Should be >1 && < %d but is %d", palette.size(), length));
        }
        for (int i = 0; i < length; i++) {
            final int alpha = 0xff & bytes[position + i];
            palette.argbArray[i] = alpha << 24 | palette.argbArray[i] & 0x00FFFFFF;
        }
    }

    @Override
    public void processTransparentGreyscale(byte k1, byte k0) throws PngException {
        scanlineProcessor.processTransparentGreyscale(k1, k0);
    }

    @Override
    public void processTransparentTruecolour(byte r1, byte r0, byte g1, byte g0, byte b1, byte b0) throws PngException {
        scanlineProcessor.processTransparentTruecolour(r1, r0, g1, g0, b1, b0);
    }

}
