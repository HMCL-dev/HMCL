// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL

// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.image.apng.error;

import org.jackhuang.hmcl.image.apng.PngConstants;

/**
 * A PngIntegrityException is thrown when some aspect of the PNG file being
 * loaded is invalid or unacceptable according to the PNG Specification.
 * <p>
 * For example, requesting a 16-bit palette image is invalid, or a colour type
 * outside of the allowed set.
 */
public class PngIntegrityException extends PngException {
    public PngIntegrityException(String message) {
        super(PngConstants.ERROR_INTEGRITY, message);
    }
}
