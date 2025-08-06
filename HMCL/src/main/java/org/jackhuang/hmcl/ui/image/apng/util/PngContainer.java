// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.util;

import org.jackhuang.hmcl.ui.image.apng.chunks.*;
import org.jackhuang.hmcl.ui.image.apng.map.PngChunkMap;

import java.util.ArrayList;
import java.util.List;

/**
 * A PngContainer represents the parsed content of a PNG file.
 * <p>
 * The original idea was that all implementations can use this as a "container"
 * for representing the data, but I think it is too generic to be useful.
 * <p>
 * WARNING: not sure if this API will remain.
 * </p>
 */
public class PngContainer {
    public List<PngChunkMap> chunks = new ArrayList<>(4);
    public PngHeader header;
    public PngGamma gamma;
    public PngPalette palette;
    //PngTransparency transarency;
    //PngBackground background;

    public PngAnimationControl animationControl;
    public List<PngFrameControl> animationFrames;
    public PngFrameControl currentFrame;
    public boolean hasDefaultImage = false;

    public PngHeader getHeader() {
        return header;
    }

    public PngGamma getGamma() {
        return gamma;
    }

    public boolean isAnimated() {
        return animationControl != null;// && animationControl.numFrames > 1;
    }

    /* TODO need?
    public PngAnimationControl getAnimationControl() {
        return animationControl;
    }

    public List<PngFrameControl> getAnimationFrames() {
        return animationFrames;
    }

    public PngFrameControl getCurrentAnimationFrame() throws PngException {
        if (animationFrames.isEmpty()) {
            throw new PngIntegrityException("No animation frames yet");
        }
        return animationFrames.get(animationFrames.size()-1);
    }
    */
}
