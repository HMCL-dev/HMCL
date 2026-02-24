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
package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface RemoteModRepository {

    enum Type {
        MOD,
        MODPACK,
        RESOURCE_PACK,
        SHADER_PACK,
        WORLD,
        CUSTOMIZATION
    }

    Type getType();

    enum SortType {
        POPULARITY,
        NAME,
        DATE_CREATED,
        LAST_UPDATED,
        AUTHOR,
        TOTAL_DOWNLOADS
    }

    enum SortOrder {
        ASC,
        DESC
    }

    class SearchResult {
        private final Stream<RemoteMod> sortedResults;

        private final Stream<RemoteMod> unsortedResults;

        private final int totalPages;

        public SearchResult(Stream<RemoteMod> sortedResults, Stream<RemoteMod> unsortedResults, int totalPages) {
            this.sortedResults = sortedResults;
            this.unsortedResults = unsortedResults;
            this.totalPages = totalPages;
        }

        public SearchResult(Stream<RemoteMod> sortedResults, int pages) {
            this.sortedResults = sortedResults;
            this.unsortedResults = sortedResults;
            this.totalPages = pages;
        }

        public Stream<RemoteMod> getResults() {
            return this.sortedResults;
        }

        public Stream<RemoteMod> getUnsortedResults() {
            return this.unsortedResults;
        }

        public int getTotalPages() {
            return this.totalPages;
        }
    }

    SearchResult search(DownloadProvider downloadProvider, String gameVersion, @Nullable Category category, int pageOffset, int pageSize, String searchFilter, SortType sortType, SortOrder sortOrder)
            throws IOException;

    Optional<RemoteMod.Version> getRemoteVersionByLocalFile(LocalModFile localModFile, Path file) throws IOException;

    RemoteMod getModById(String id) throws IOException;

    default RemoteMod resolveDependency(String id) throws IOException {
        return getModById(id);
    }

    RemoteMod.File getModFile(String modId, String fileId) throws IOException;

    Stream<RemoteMod.Version> getRemoteVersionsById(String id) throws IOException;

    Stream<Category> getCategories() throws IOException;

    class Category {
        private final Object self;
        private final String id;
        private final List<Category> subcategories;

        public Category(Object self, String id, List<Category> subcategories) {
            this.self = self;
            this.id = id;
            this.subcategories = subcategories;
        }

        public Object getSelf() {
            return self;
        }

        public String getId() {
            return id;
        }

        public List<Category> getSubcategories() {
            return subcategories;
        }
    }
}
