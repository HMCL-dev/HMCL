/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
