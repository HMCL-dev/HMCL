// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.image;

import javafx.scene.image.Image;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

/// Decodes image data from an input stream into a JavaFX image.
///
/// Supported formats, animation support, and scaling behavior depend on the
/// implementation. Convenience methods load files and URLs with the same decoder
/// and close the streams they open.
@FunctionalInterface
@NotNullByDefault
public interface ImageLoader {

    /// Loads an image file at its intrinsic size.
    ///
    /// @implSpec Delegates to [#load(Path, int, int, boolean, boolean)] with
    /// zero dimensions and both flags set to `false`.
    /// @param path the image file
    /// @return the loaded image, never `null`
    /// @throws Exception if opening, loading, or closing the file fails
    default Image load(Path path) throws Exception {
        return load(path, 0, 0, false, false);
    }

    /// Loads an image file with the requested scaling options.
    ///
    /// The file stream is closed on both success and failure. Scaling options
    /// have the same meaning as in [#load(InputStream, int, int, boolean, boolean)].
    ///
    /// @implSpec Opens the file and delegates to
    /// [#load(InputStream, String, int, int, boolean, boolean)], passing the
    /// final path component as the file name, or an empty string if absent.
    /// @param path the image file
    /// @param requestedWidth the requested width in pixels; zero requests no explicit width
    /// @param requestedHeight the requested height in pixels; zero requests no explicit height
    /// @param preserveRatio whether scaling should preserve the source aspect ratio
    /// @param smooth whether to request higher-quality filtering when scaling
    /// @return the loaded image, never `null`
    /// @throws Exception if opening, loading, or closing the file fails
    default Image load(Path path,
                       int requestedWidth, int requestedHeight,
                       boolean preserveRatio, boolean smooth) throws Exception {
        @Nullable Path fileName = path.getFileName();
        try (InputStream input = Files.newInputStream(path)) {
            return load(input, fileName == null ? "" : fileName.toString(),
                    requestedWidth, requestedHeight, preserveRatio, smooth);
        }
    }

    /// Loads an image from a named stream at its intrinsic size and closes the stream.
    ///
    /// @implSpec Delegates to [#load(InputStream, String, int, int, boolean, boolean)]
    /// with zero dimensions and both flags set to `false`.
    /// @param input the stream containing encoded image data
    /// @param fileName a file-name hint for implementations that use it to select a decoder
    /// @return the loaded image, never `null`
    /// @throws Exception if loading the image or closing the stream fails
    default Image load(InputStream input, String fileName) throws Exception {
        return load(input, fileName, 0, 0, false, false);
    }

    /// Loads an image from a named stream with the requested scaling options and
    /// closes the stream on both success and failure.
    ///
    /// Reading starts at the current stream position. Scaling options have the
    /// same meaning as in [#load(InputStream, int, int, boolean, boolean)].
    ///
    /// @implSpec Wraps the stream in a [BufferedInputStream] and delegates to
    /// [#load(InputStream, int, int, boolean, boolean)]. The file-name hint is
    /// ignored; this method uses the current loader without selecting another decoder.
    /// @param input the stream containing encoded image data; ownership is transferred to this method
    /// @param fileName a file-name hint for implementations that use it to select a decoder
    /// @param requestedWidth the requested width in pixels; zero requests no explicit width
    /// @param requestedHeight the requested height in pixels; zero requests no explicit height
    /// @param preserveRatio whether scaling should preserve the source aspect ratio
    /// @param smooth whether to request higher-quality filtering when scaling
    /// @return the loaded image, never `null`
    /// @throws Exception if loading the image or closing the stream fails
    default Image load(InputStream input, String fileName,
                       int requestedWidth, int requestedHeight,
                       boolean preserveRatio, boolean smooth) throws Exception {
        try (var bufferedInput = new BufferedInputStream(input)) {
            return load(bufferedInput, requestedWidth, requestedHeight, preserveRatio, smooth);
        }
    }

    /// Loads an image from a URL at its intrinsic size.
    ///
    /// @implSpec Delegates to [#load(WebURL, int, int, boolean, boolean)] with
    /// zero dimensions and both flags set to `false`.
    /// @param url the image location
    /// @return the loaded image, never `null`
    /// @throws Exception if connecting, loading the image, or closing the response stream fails
    default Image load(WebURL url) throws Exception {
        return load(url, 0, 0, false, false);
    }

    /// Loads an image from a URL with the requested scaling options.
    ///
    /// This method may block while connecting, reading, or loading the image.
    /// The response stream is closed on both success and failure. Scaling options
    /// have the same meaning as in [#load(InputStream, int, int, boolean, boolean)].
    ///
    /// @implSpec Opens a connection using [NetworkUtils#createConnection(WebURL)]
    /// and follows HTTP redirects using [NetworkUtils#resolveConnection(HttpURLConnection)].
    /// Wraps the response stream in a [BufferedInputStream] and delegates to
    /// [#load(InputStream, int, int, boolean, boolean)]. Neither the URL file name
    /// nor the response media type is used to select another decoder.
    /// @param url the image location
    /// @param requestedWidth the requested width in pixels; zero requests no explicit width
    /// @param requestedHeight the requested height in pixels; zero requests no explicit height
    /// @param preserveRatio whether scaling should preserve the source aspect ratio
    /// @param smooth whether to request higher-quality filtering when scaling
    /// @return the loaded image, never `null`
    /// @throws Exception if connecting, loading the image, or closing the response stream fails
    default Image load(WebURL url,
                       int requestedWidth, int requestedHeight,
                       boolean preserveRatio, boolean smooth) throws Exception {
        URLConnection connection = NetworkUtils.createConnection(url);
        if (connection instanceof HttpURLConnection httpConnection) {
            connection = NetworkUtils.resolveConnection(httpConnection);
        }

        try (var input = new BufferedInputStream(connection.getInputStream())) {
            return load(input, requestedWidth, requestedHeight, preserveRatio, smooth);
        }
    }

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
