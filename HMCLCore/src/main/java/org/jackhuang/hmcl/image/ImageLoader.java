// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.image;

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

/// Decodes image data from an input stream.
///
/// Supported formats, animation support, and scaling behavior depend on the
/// implementation. Convenience methods load files and URLs with the same decoder
/// and close the streams they open.
///
/// @param <I> the decoded image type
@FunctionalInterface
@NotNullByDefault
public interface ImageLoader<I> {

    /// Loads an image file at its intrinsic size.
    ///
    /// @implSpec Delegates to [#load(Path, int, int, boolean, boolean)] with
    /// zero dimensions and both flags set to `false`.
    /// @param path the image file
    /// @return the loaded image, never `null`
    /// @throws Exception if opening, loading, or closing the file fails
    default I load(Path path) throws Exception {
        return load(path, 0, 0, false, false);
    }

    /// Loads an image file with the requested scaling options.
    ///
    /// The file stream is closed on both success and failure. Scaling options
    /// have the same meaning as in [#load(InputStream, String, int, int, boolean, boolean)].
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
    default I load(Path path,
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
    /// @implSpec Wraps the stream in a [BufferedInputStream] and delegates to
    /// [#load(InputStream, String, int, int, boolean, boolean)] with zero dimensions
    /// and both flags set to `false`. Closes the stream on both success and failure.
    /// @param input the stream containing encoded image data
    /// @param fileName a file-name hint for implementations that use it to select a decoder
    /// @return the loaded image, never `null`
    /// @throws Exception if loading the image or closing the stream fails
    default I load(InputStream input, String fileName) throws Exception {
        try (var bufferedInput = new BufferedInputStream(input)) {
            return load(bufferedInput, fileName, 0, 0, false, false);
        }
    }

    /// Loads an image from a stream without a file-name hint.
    ///
    /// Stream ownership and scaling options have the same meaning as in
    /// [#load(InputStream, String, int, int, boolean, boolean)].
    ///
    /// @implSpec Delegates to [#load(InputStream, String, int, int, boolean, boolean)]
    /// with an empty file-name hint.
    /// @param input the stream containing encoded image data
    /// @param requestedWidth the requested width in pixels; zero requests no explicit width
    /// @param requestedHeight the requested height in pixels; zero requests no explicit height
    /// @param preserveRatio whether scaling should preserve the source aspect ratio
    /// @param smooth whether to request higher-quality filtering when scaling
    /// @return the loaded image, never `null`
    /// @throws Exception if reading, decoding, or rendering the image fails
    default I load(InputStream input,
                   int requestedWidth, int requestedHeight,
                   boolean preserveRatio, boolean smooth) throws Exception {
        return load(input, "", requestedWidth, requestedHeight, preserveRatio, smooth);
    }

    /// Loads an image from a URL at its intrinsic size.
    ///
    /// @implSpec Delegates to [#load(WebURL, int, int, boolean, boolean)] with
    /// zero dimensions and both flags set to `false`.
    /// @param url the image location
    /// @return the loaded image, never `null`
    /// @throws Exception if connecting, loading the image, or closing the response stream fails
    default I load(WebURL url) throws Exception {
        return load(url, 0, 0, false, false);
    }

    /// Loads an image from a URL with the requested scaling options.
    ///
    /// This method may block while connecting, reading, or loading the image.
    /// The response stream is closed on both success and failure. Scaling options
    /// have the same meaning as in [#load(InputStream, String, int, int, boolean, boolean)].
    ///
    /// @implSpec Opens a connection using [NetworkUtils#createConnection(WebURL)]
    /// and follows HTTP redirects using [NetworkUtils#resolveConnection(HttpURLConnection)].
    /// Wraps the response stream in a [BufferedInputStream] and delegates to
    /// [#load(InputStream, String, int, int, boolean, boolean)], passing the final
    /// path segment of the resolved URL as the file-name hint. Percent escapes
    /// are preserved, and an empty path or trailing slash produces an empty hint.
    /// The response media type is not used to select another decoder.
    /// @param url the image location
    /// @param requestedWidth the requested width in pixels; zero requests no explicit width
    /// @param requestedHeight the requested height in pixels; zero requests no explicit height
    /// @param preserveRatio whether scaling should preserve the source aspect ratio
    /// @param smooth whether to request higher-quality filtering when scaling
    /// @return the loaded image, never `null`
    /// @throws Exception if connecting, loading the image, or closing the response stream fails
    default I load(WebURL url,
                   int requestedWidth, int requestedHeight,
                   boolean preserveRatio, boolean smooth) throws Exception {
        URLConnection connection = NetworkUtils.createConnection(url);
        if (connection instanceof HttpURLConnection httpConnection) {
            connection = NetworkUtils.resolveConnection(httpConnection);
        }

        try (var input = new BufferedInputStream(connection.getInputStream())) {
            String path = connection.getURL().getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            return load(input, fileName, requestedWidth, requestedHeight, preserveRatio, smooth);
        }
    }

    /// Loads an image from the current stream position using an optional file-name hint.
    ///
    /// The file name is metadata, not a path to open. Implementations may use it
    /// to select a decoder or ignore it. An empty string indicates that no name
    /// is available.
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
    /// @param fileName the non-null file-name hint, or an empty string if unknown
    /// @param requestedWidth the requested width in pixels; zero requests no explicit width
    /// @param requestedHeight the requested height in pixels; zero requests no explicit height
    /// @param preserveRatio whether scaling should preserve the source aspect ratio
    /// @param smooth whether to request higher-quality filtering when scaling
    /// @return the loaded image, never `null`
    /// @throws Exception if reading, decoding, or rendering the image fails
    I load(InputStream input, String fileName,
           int requestedWidth, int requestedHeight,
           boolean preserveRatio, boolean smooth) throws Exception;
}
