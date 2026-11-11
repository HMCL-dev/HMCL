// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL

// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.image.apng.error;

import org.jackhuang.hmcl.image.apng.PngConstants;

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
