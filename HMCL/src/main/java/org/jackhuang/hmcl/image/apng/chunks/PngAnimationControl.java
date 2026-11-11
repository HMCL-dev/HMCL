// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL

// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.image.apng.chunks;

import org.jetbrains.annotations.NotNull;

/**
 * A PngAnimationControl object contains data parsed from the ``acTL``
 * animation control chunk of an animated PNG file.
 */
public record PngAnimationControl(int numFrames, int numPlays) {

    public boolean loopForever() {
        return 0 == numPlays;
    }

    @Override
    public @NotNull String toString() {
        return "PngAnimationControl{" +
                "numFrames=" + numFrames +
                ", numPlays=" + numPlays +
                '}';
    }
}
