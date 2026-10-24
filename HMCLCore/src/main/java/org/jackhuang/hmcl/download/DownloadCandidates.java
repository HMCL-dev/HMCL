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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.stream.Stream;

@NotNullByDefault
public final class DownloadCandidates {
    public static DownloadCandidates of(String url) {
        return new DownloadCandidates(List.of(DownloadCandidate.of(url)));
    }

    public static DownloadCandidates of(WebURL url) {
        return new DownloadCandidates(List.of(DownloadCandidate.of(url)));
    }

    public static DownloadCandidates of(DownloadCandidate candidate) {
        return new DownloadCandidates(List.of(candidate));
    }

    public static DownloadCandidates of(List<DownloadCandidate> candidates) {
        return new DownloadCandidates(candidates);
    }

    public static DownloadCandidates of(String... urls) {
        return new DownloadCandidates(Stream.of(urls).map(DownloadCandidate::of).toList());
    }

    public static DownloadCandidates of(WebURL... urls) {
        return new DownloadCandidates(Stream.of(urls).map(DownloadCandidate::of).toList());
    }

    public static DownloadCandidates of(DownloadCandidate... candidates) {
        return new DownloadCandidates(List.of(candidates));
    }

    public static DownloadCandidates ofUrls(List<WebURL> urls) {
        return new DownloadCandidates(urls.stream().map(DownloadCandidate::of).toList());
    }

    private final @Unmodifiable List<DownloadCandidate> candidates;

    private DownloadCandidates(@Unmodifiable List<DownloadCandidate> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Candidates must not be empty");
        }

        this.candidates = List.copyOf(candidates);
    }

    public DownloadCandidate getPrimaryCandidate() {
        return candidates.get(0);
    }

    public @Unmodifiable List<DownloadCandidate> getCandidates() {
        return candidates;
    }
}
