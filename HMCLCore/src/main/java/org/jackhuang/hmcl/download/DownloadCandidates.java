// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
