// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.image;

import javafx.scene.image.Image;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.InputStream;

/// Decodes image data from an input stream into a JavaFX image.
///
/// Supported formats, animation support, and scaling behavior depend on the
/// implementation.
@NotNullByDefault
public interface ImageLoader {

    /// Loads an image from the current position of the supplied stream.
    ///
    /// This method performs loading synchronously and may block while reading or
    /// waiting for rendering. The stream may be partially or fully consumed even
    /// if loading fails; its position is not restored. Implementations may close
    /// the stream, so callers must not assume it remains usable and are responsible
    /// for ensuring it is closed on both success and failure.
    ///
    /// Requested dimensions and scaling flags are interpreted by the implementation.
    /// In particular, some loaders scale only when both dimensions are positive,
    /// and some ignore `smooth`. The returned dimensions may therefore differ from
    /// the requested dimensions.
    ///
    /// @param input the non-null stream containing encoded image data
    /// @param requestedWidth the requested width in pixels; zero requests no explicit width
    /// @param requestedHeight the requested height in pixels; zero requests no explicit height
    /// @param preserveRatio whether scaling should preserve the source aspect ratio
    /// @param smooth whether to request higher-quality filtering when scaling
    /// @return the loaded image, never `null`
    /// @throws Exception if reading, decoding, or rendering the image fails
    Image load(InputStream input,
               int requestedWidth, int requestedHeight,
               boolean preserveRatio, boolean smooth) throws Exception;
}
