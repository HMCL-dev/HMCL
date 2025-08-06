// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.error;

import org.jackhuang.hmcl.ui.image.apng.PngConstants;

/**
 * A PngFeatureException is thrown when a feature used in a specific file
 * is not supported by the pipeline being used. For example, at the time
 * of writing, interlaced image reading is not supported.
 */
public class PngFeatureException extends PngException {
    public PngFeatureException(String message) {
        super(PngConstants.ERROR_FEATURE_NOT_SUPPORTED, message);
    }
}
