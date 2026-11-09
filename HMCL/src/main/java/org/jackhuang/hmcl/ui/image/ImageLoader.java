// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.image;

import javafx.scene.image.Image;

import java.io.InputStream;

public interface ImageLoader {
    Image load(InputStream input,
               int requestedWidth, int requestedHeight,
               boolean preserveRatio, boolean smooth) throws Exception;
}
