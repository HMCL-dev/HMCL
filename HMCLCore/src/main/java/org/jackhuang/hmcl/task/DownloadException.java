// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.glavo.url.WebURL;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/// Reports failure to download a candidate URL, retaining the underlying cause.
public class DownloadException extends IOException {

    /// Candidate URL whose download failed.
    private final String url;

    /// Creates a failure for the candidate URL and a non-null cause.
    public DownloadException(WebURL url, @NotNull Throwable cause) {
        super("Unable to download " + url + ", " + cause.getMessage(), requireNonNull(cause));
        this.url = url.href();
    }

    /// Creates a failure for the candidate URL and a non-null cause.
    public DownloadException(String url, @NotNull Throwable cause) {
        super("Unable to download " + url + ", " + cause.getMessage(), requireNonNull(cause));
        this.url = url;
    }

    /// Returns the candidate URL before any redirects.
    public String getUrl() {
        return url;
    }
}
