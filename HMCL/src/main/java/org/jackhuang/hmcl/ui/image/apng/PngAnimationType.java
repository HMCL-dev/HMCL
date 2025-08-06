// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng;

/**
 * A PNG file has an animation type - most commonly, not animated.
 * <p>
 * This is primarily relevant to tracking whether the default image in a PNG
 * is to be
 * a) processed as normal (e.g. in a standard or "non-animated" PNG file);
 * b) processed and used as the first frame in an animated PNG; or
 * c) not processed and discarded (e.g. in an animated PNG which does not use the first frame).
 */
public enum PngAnimationType {
    NOT_ANIMATED,
    ANIMATED_KEEP_DEFAULT_IMAGE,
    ANIMATED_DISCARD_DEFAULT_IMAGE;
}
