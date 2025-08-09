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

import org.jackhuang.hmcl.ui.image.apng.PngScanlineBuffer;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngAnimationControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngFrameControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngHeader;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.internal.BgraPreFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Glavo
 */
public final class BgraPreBitmapDirector extends BasicArgb8888Director<Argb8888BitmapSequence> {
    private PngHeader header;
    private Argb8888Bitmap defaultImage;
    private byte[] canvas;

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
        this.canvas = new byte[header.width * header.height * 4];
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
    public void receiveFrameImage(Argb8888Bitmap bitmap) {
        if (null == currentFrame) {
            throw new IllegalStateException("Received a frame image with no frame control in place");
        }
        if (null == animationFrames) {
            throw new IllegalStateException("Received a frame image without animation control (or frame list?) in place");
        }



        // TODO
        // animationFrames.add(new Argb8888BitmapSequence.Frame(currentFrame, bitmap));
        currentFrame = null;
    }

    @Override
    public Argb8888BitmapSequence getResult() {
        return bitmapSequence;
    }
}

