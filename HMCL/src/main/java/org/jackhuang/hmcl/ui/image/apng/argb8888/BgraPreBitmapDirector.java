/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.image.apng.argb8888;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.ui.image.apng.PngScanlineBuffer;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngAnimationControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngFrameControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngHeader;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.error.PngIntegrityException;
import org.jackhuang.hmcl.ui.image.internal.BgraPreAnimationImage;
import org.jackhuang.hmcl.ui.image.internal.BgraPreCanvas;
import org.jackhuang.hmcl.ui.image.internal.BgraPreFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Glavo
 */
public final class BgraPreBitmapDirector extends BasicArgb8888Director<Image> {
    private PngHeader header;
    private Argb8888Bitmap defaultImage;
    private BgraPreCanvas canvas;

    private PngFrameControl currentFrame = null;
    private PngAnimationControl animationControl;
    private List<BgraPreFrame> animationFrames;

    @Override
    public void receiveHeader(PngHeader header, PngScanlineBuffer buffer) throws PngException {
        if (this.header != null)
            throw new IllegalStateException("Png header has already been set");

        this.header = header;
        this.defaultImage = new Argb8888Bitmap(header.width, header.height);
        this.scanlineProcessor = Argb8888Processors.from(header, buffer, defaultImage);
        this.canvas = new BgraPreCanvas(header.width, header.height);
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
        // this.bitmapSequence.receiveDefaultImage(bitmap);
    }

    @Override
    public void receiveAnimationControl(PngAnimationControl control) {
        this.animationControl = animationControl;
        this.animationFrames = new ArrayList<>(animationControl.numFrames);
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
    public void receiveFrameImage(Argb8888Bitmap bitmap) throws PngException {
        if (currentFrame == null)
            throw new IllegalStateException("Received a frame image with no frame control in place");
        if (animationFrames == null)
            throw new IllegalStateException("Received a frame image without animation control (or frame list?) in place");

        final long duration;
        if (currentFrame.delayNumerator == 0) {
            duration = 10;
        } else {
            int durationsMills = 1000 * currentFrame.delayNumerator;
            if (currentFrame.delayDenominator == 0)
                durationsMills /= 100;
            else
                durationsMills /= currentFrame.delayDenominator;
            duration = durationsMills;
        }

        final var frame = new BgraPreFrame(
                canvas,
                currentFrame.width, currentFrame.height, currentFrame.xOffset, currentFrame.yOffset,
                duration
        );

        if (currentFrame.blendOp == 0) {
            frame.setArgb(currentFrame.xOffset, currentFrame.yOffset, currentFrame.width, currentFrame.height,
                    bitmap.array, 0, currentFrame.width);
        } else if (currentFrame.blendOp == 1) { // APNG_BLEND_OP_OVER - Alpha blending
            frame.blendingWithArgb(currentFrame.xOffset, currentFrame.yOffset, currentFrame.width, currentFrame.height,
                    bitmap.array, 0, currentFrame.width);
        } else {
            throw new PngIntegrityException("Unsupported blendOp " + currentFrame.blendOp);
        }

        switch (currentFrame.disposeOp) {
            case 0:  // APNG_DISPOST_OP_NONE
                canvas.setBgraPre(currentFrame.xOffset, currentFrame.yOffset, frame);
                break;
            case 1: // APNG_DISPOSE_OP_BACKGROUND
                canvas.clear(currentFrame.xOffset, currentFrame.yOffset, currentFrame.width, currentFrame.height);
                break;
            case 2: // APNG_DISPOSE_OP_PREVIOUS
                // Do nothing, keep the previous frame.
                break;
            default:
                throw new PngIntegrityException("Unsupported disposeOp " + currentFrame.disposeOp);
        }

        currentFrame = null;
    }

    @Override
    public Image getResult() {
        return null; // TODO
    }
}

