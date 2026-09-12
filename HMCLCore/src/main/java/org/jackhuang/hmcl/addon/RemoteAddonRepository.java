/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

    RemoteAddon.Type getType();

    String getApiBaseUrl();

    String getBaseUrl();

    SearchResult search(DownloadProvider downloadProvider, String gameVersion, @Nullable Category category, int pageOffset, int pageSize, String searchFilter, SortType sortType, SortOrder sortOrder)
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

    Stream<Category> getCategories() throws IOException;

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
    }
}
