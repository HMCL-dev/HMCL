// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.argb8888;

import org.jackhuang.hmcl.ui.image.apng.chunks.PngAnimationControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngFrameControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngHeader;

import java.util.ArrayList;
import java.util.List;

/**
 * An Argb8888BitmapSequence object represents all bitmaps in a single PNG file,
 * whether it has one and only one default image, any number of frames in an animation
 * or a default image and a separate set of animation frames.
 * <p>
 * Note that instances of this class will hold an individual bitmap for every frame
 * and does <em>not</em> do composition of images in any way. Composition is done in
 * the japng_android library using an Android Canvas. This class is not used for that,
 * as the intermediate Argb8888Bitmap objects are not required, only one bitmap and
 * the output buffer is required during composition.
 */
public final class Argb8888BitmapSequence {

    public final PngHeader header;
    public final Argb8888Bitmap defaultImage;

    private boolean defaultImageIsSet = false;
    private PngAnimationControl animationControl;
    List<Frame> animationFrames;

    public Argb8888BitmapSequence(PngHeader header) {
        this.header = header;
        this.defaultImage = new Argb8888Bitmap(header.width, header.height);
    }

    public void receiveAnimationControl(PngAnimationControl animationControl) {
        this.animationControl = animationControl;
        this.animationFrames = new ArrayList<>(animationControl.numFrames);
    }

    public void receiveDefaultImage(Argb8888Bitmap bitmap) {
        defaultImageIsSet = true;
    }

    public boolean hasDefaultImage() {
        return defaultImageIsSet;
    }

    public boolean isAnimated() {
        return null != animationControl && animationControl.numFrames > 0;
    }

    public PngAnimationControl getAnimationControl() {
        return animationControl;
    }

    public List<Frame> getAnimationFrames() {
        return animationFrames;
    }

    public static final class Frame {
        public final PngFrameControl control;
        public final Argb8888Bitmap bitmap;

        public Frame(PngFrameControl control, Argb8888Bitmap bitmap) {
            this.control = control;
            this.bitmap = bitmap;
        }
    }
}
