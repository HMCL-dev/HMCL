// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download;

import org.glavo.url.WebURL;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/// @author Glavo
public record DownloadCandidate(
        @Nullable WebURL url,
        @Nullable String rawUrl,
        int retry,
        @Nullable Duration connectTimeout,
        @Nullable Duration readTimeout
) {

    public static DownloadCandidate of(WebURL url) {
        return new DownloadCandidate(url, null, -1, null, null);
    }

    public static DownloadCandidate of(String url) {
        return new DownloadCandidate(WebURL.tryParse(url), url, -1, null, null);
    }

    public DownloadCandidate {
        assert url != null || rawUrl != null;
    }

    public String displayUrl() {
        return url != null ? url.href() : rawUrl;
    }
}
