// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.util;

/**
 * Returns the PngContainer built by the PngContainerProcessor.
 * WARNING: this may be removed.
 */
public class PngContainerBuilder extends PngContainerProcessor<PngContainer> {

//    @Override
//    public PngAnimationType chooseApngImageType(PngAnimationType type, PngFrameControl currentFrame) throws PngException {
//        return type;
//    }

    @Override
    public PngContainer getResult() {
        return getContainer();
    }
}
