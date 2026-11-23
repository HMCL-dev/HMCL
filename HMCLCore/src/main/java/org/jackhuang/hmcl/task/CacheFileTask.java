// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DownloadCandidate;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.UrlResponseInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Downloads a remote file to a cache repository. Returns the cached path, or null
/// if the server's cache policy prevents storage and no checksum was supplied.
///
/// @author Glavo
public final class CacheFileTask extends FetchTask<@Nullable Path> {

    /// Expected SHA-1 checksum, or `null` when the remote cache policy determines reuse.
    private final @Nullable String expectedSha1;

    /// Creates a task for one URL string using remote cache metadata.
    ///
    /// @param url the HTTP or HTTPS URL string
    public CacheFileTask(@NotNull String url) {
        this(WebURL.parse(url));
    }

    /// Creates a task for one URL using remote cache metadata.
    ///
    /// @param url the HTTP or HTTPS URL
    public CacheFileTask(@NotNull WebURL url) {
        this(List.of(url));
    }

    /// Creates a task for candidate URLs using remote cache metadata.
    ///
    /// @param urls candidate download URLs in attempt order
    public CacheFileTask(@NotNull List<@NotNull WebURL> urls) {
        this(DownloadCandidates.ofUrls(urls));
    }

    public CacheFileTask(DownloadCandidates candidates) {
        super(candidates);
        this.expectedSha1 = null;
        validateUris(candidates);
        setName(new TaskName.Simple(candidates.getPrimaryCandidate().displayUrl()));
    }

    public CacheFileTask(DownloadCandidates candidates, String expectedSha1) {
        super(candidates);
        if (!DigestUtils.isSha1Digest(expectedSha1)) {
            throw new IllegalArgumentException("Invalid SHA-1 checksum: " + expectedSha1);
        }
        this.expectedSha1 = expectedSha1.toLowerCase(Locale.ROOT);
        validateUris(candidates);
        setName(new TaskName.Simple(candidates.getPrimaryCandidate().displayUrl()));
    }

    /// Verifies that all candidate URLs use HTTP or HTTPS.
    ///
    /// @param urls the candidate URLs
    private static void validateUris(@NotNull DownloadCandidates urls) {
        for (DownloadCandidate candidate : urls.getCandidates()) {
            if (candidate.url() == null || !NetworkUtils.isHttpUri(candidate.url())) {
                throw new IllegalArgumentException("Invalid URL: " + candidate.displayUrl());
            }
        }
    }

    /// Selects a verified content-addressed entry or the applicable remote-cache policy.
    ///
    /// @return the reusable file, or null on a cache miss
    @Override
    protected @Nullable Path findCachedFile() {
        if (expectedSha1 != null) {
            Optional<Path> cached = repository.checkExistentFile(
                    null, CacheRepository.SHA1, expectedSha1);
            if (cached.isPresent()) {
                LOG.info("Using cached file with SHA-1 " + expectedSha1);
                return cached.get();
            }
            return null;
        }

        // Check cache
        for (DownloadCandidate candidate : candidates.getCandidates()) {
            WebURL url = candidate.url();
            if (url == null) continue;

            try {
                Path cached = repository.getCachedRemoteFile(url, true);
                LOG.info("Using cached file for " + NetworkUtils.dropQuery(url));
                return cached;
            } catch (CacheRepository.CacheExpiredException e) {
                LOG.info("Cache expired for " + NetworkUtils.dropQuery(url));
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    @Override
    protected boolean shouldCheckETag() {
        return expectedSha1 == null;
    }

    @Override
    protected Path useCachedResult(Path cache) {
        return cache;
    }

    /// Creates a temporary sink that publishes a successful download to the cache repository.
    ///
    /// @param response     the HTTP response metadata
    /// @param checkETag    whether remote cache metadata is being checked
    /// @param bmclapiHash  the hash supplied by BMCLAPI, or `null`
    /// @return the temporary download sink
    /// @throws IOException if the temporary file cannot be created
    @Override
    protected Context<@Nullable Path> getContext(@Nullable UrlResponseInfo response, boolean checkETag, @Nullable String bmclapiHash) throws IOException {
        if (expectedSha1 == null && (!checkETag || response == null)) {
            throw new IOException("Remote response metadata is unavailable");
        }

        return new FileContext<>(null) {
            @Override
            @Nullable Path commit(Path temp) throws IOException {
                if (expectedSha1 != null) {
                    ChecksumMismatchException.verifyChecksum(temp, CacheRepository.SHA1, expectedSha1);
                    return repository.cacheFile(temp, CacheRepository.SHA1, expectedSha1);
                }
                return repository.cacheRemoteFile(Objects.requireNonNull(response), temp);
            }
        };
    }
}
