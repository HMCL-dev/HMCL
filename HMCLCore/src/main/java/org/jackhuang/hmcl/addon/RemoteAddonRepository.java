// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface RemoteAddonRepository {

    String getApiBaseUrl();

    String getBaseUrl();

    SearchResult search(DownloadProvider downloadProvider, RemoteAddon.Type type, String gameVersion, @Nullable Category category, int pageOffset, int pageSize, String searchFilter, SortType sortType, SortOrder sortOrder)
            throws IOException;

    Optional<RemoteAddon.Version> getRemoteVersionByLocalFile(Path file) throws IOException;

    RemoteAddon getAddonById(DownloadProvider downloadProvider, String id) throws IOException;

    /// @return the dependency resolved, or {@link RemoteAddon#BROKEN} when the dependency is not found
    /// @throws IOException if an I/O error occurs, except for when the desired dependency is not found
    RemoteAddon resolveDependency(DownloadProvider downloadProvider, String id) throws IOException;

    RemoteAddon.File getAddonFile(String projectId, String fileId) throws IOException;

    Stream<RemoteAddon.Version> getRemoteVersionsById(DownloadProvider downloadProvider, String id) throws IOException;

    @Nullable
    String getAddonChangelog(DownloadProvider downloadProvider, String addonId, String versionId) throws IOException;

    @NotNull
    String getVersionPageUrl(RemoteAddon.Version version) throws IOException;

    Stream<Category> getCategories(RemoteAddon.Type type) throws IOException;

    record Category(Object self, String id, List<Category> subcategories) {
    }

    enum SortType {
        RELEVANCY,
        POPULARITY,
        DATE_CREATED,
        LAST_UPDATED,
        TOTAL_DOWNLOADS
    }

    enum SortOrder {
        ASC,
        DESC
    }

    record SearchResult(Stream<RemoteAddon> results, int totalPages) {
        public static SearchResult empty() {
            return new SearchResult(Stream.empty(), 0);
        }

    }
}
