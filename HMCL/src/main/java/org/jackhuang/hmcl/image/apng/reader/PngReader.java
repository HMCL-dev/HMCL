// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL

// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.image.apng.reader;

import org.jackhuang.hmcl.image.apng.error.PngException;

import java.io.IOException;

/**
 * All PngReader implementations need to read a specific single chunk and to return
 * a result of some form.
 */
public interface PngReader<ResultT> {
    boolean readChunk(PngSource source, int code, int dataLength) throws PngException, IOException;

    void finishedChunks(PngSource source) throws PngException, IOException;

    ResultT getResult();
}
