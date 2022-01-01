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
package org.jackhuang.hmcl.mod.modrinth;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.Hex;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

public final class ModrinthRemoteModRepository implements RemoteModRepository {
    public static final ModrinthRemoteModRepository INSTANCE = new ModrinthRemoteModRepository();

    private static final String PREFIX = "https://api.modrinth.com";

    private ModrinthRemoteModRepository() {
    }

    private static String convertSortType(SortType sortType) {
        switch (sortType) {
            case DATE_CREATED:
                return "newest";
            case POPULARITY:
            case NAME:
            case AUTHOR:
                return "relevance";
            case LAST_UPDATED:
                return "updated";
            case TOTAL_DOWNLOADS:
                return "downloads";
            default:
                throw new IllegalArgumentException("Unsupported sort type " + sortType);
        }
    }

    public List<ModResult> searchPaginated(String gameVersion, int pageOffset, int pageSize, String searchFilter, SortType sort) throws IOException {
        Map<String, String> query = mapOf(
                pair("query", searchFilter),
                pair("offset", Integer.toString(pageOffset)),
                pair("limit", Integer.toString(pageSize)),
                pair("index", convertSortType(sort))
        );
        if (StringUtils.isNotBlank(gameVersion)) {
            query.put("version", "versions=" + gameVersion);
        }
        Response<ModResult> response = HttpRequest.GET(NetworkUtils.withQuery(PREFIX + "/api/v1/mod", query))
                .getJson(new TypeToken<Response<ModResult>>() {
                }.getType());
        return response.getHits();
    }

    @Override
    public Stream<RemoteMod> search(String gameVersion, Category category, int pageOffset, int pageSize, String searchFilter, SortType sort) throws IOException {
        return searchPaginated(gameVersion, pageOffset, pageSize, searchFilter, sort).stream()
                .map(ModResult::toMod);
    }

    @Override
    public Optional<RemoteMod.Version> getRemoteVersionByLocalFile(LocalModFile localModFile, Path file) throws IOException {
        String sha1 = Hex.encodeHex(DigestUtils.digest("SHA-1", file));

        try {
            ModVersion mod = HttpRequest.GET(PREFIX + "/api/v1/version_file/" + sha1,
                            pair("algorithm", "sha1"))
                    .getJson(ModVersion.class);
            return mod.toVersion();
        } catch (ResponseCodeException e) {
            if (e.getResponseCode() == 404) {
                return Optional.empty();
            } else {
                throw e;
            }
        }
    }

    @Override
    public RemoteMod getModById(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<RemoteMod.Version> getRemoteVersionsById(String id) throws IOException {
        id = StringUtils.removePrefix(id, "local-");
        List<ModVersion> versions = HttpRequest.GET("https://api.modrinth.com/api/v1/mod/" + id + "/version")
                .getJson(new TypeToken<List<ModVersion>>() {
                }.getType());
        return versions.stream().map(ModVersion::toVersion).flatMap(Lang::toStream);
    }

    public List<String> getCategoriesImpl() throws IOException {
        return HttpRequest.GET("https://api.modrinth.com/api/v1/tag/category").getJson(new TypeToken<List<String>>() {
        }.getType());
    }

    public Stream<Category> getCategories() throws IOException {
        return getCategoriesImpl().stream()
                .map(name -> new Category(null, name, Collections.emptyList()));
    }

    public static class Mod {
        private final String id;

        private final String slug;

        private final String team;

        private final String title;

        private final String description;

        private final Instant published;

        private final Instant updated;

        private final List<String> categories;

        private final List<String> versions;

        private final int downloads;

        @SerializedName("icon_url")
        private final String iconUrl;

        public Mod(String id, String slug, String team, String title, String description, Instant published, Instant updated, List<String> categories, List<String> versions, int downloads, String iconUrl) {
            this.id = id;
            this.slug = slug;
            this.team = team;
            this.title = title;
            this.description = description;
            this.published = published;
            this.updated = updated;
            this.categories = categories;
            this.versions = versions;
            this.downloads = downloads;
            this.iconUrl = iconUrl;
        }

        public String getId() {
            return id;
        }

        public String getSlug() {
            return slug;
        }

        public String getTeam() {
            return team;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public Instant getPublished() {
            return published;
        }

        public Instant getUpdated() {
            return updated;
        }

        public List<String> getCategories() {
            return categories;
        }

        public List<String> getVersions() {
            return versions;
        }

        public int getDownloads() {
            return downloads;
        }

        public String getIconUrl() {
            return iconUrl;
        }
    }

    public static class ModVersion implements RemoteMod.IVersion {
        private final String id;

        @SerializedName("mod_id")
        private final String modId;

        @SerializedName("author_id")
        private final String authorId;

        private final String name;

        @SerializedName("version_number")
        private final String versionNumber;

        private final String changelog;

        @SerializedName("date_published")
        private final Instant datePublished;

        private final int downloads;

        @SerializedName("version_type")
        private final String versionType;

        private final List<ModVersionFile> files;

        private final List<String> dependencies;

        @SerializedName("game_versions")
        private final List<String> gameVersions;

        private final List<String> loaders;

        public ModVersion(String id, String modId, String authorId, String name, String versionNumber, String changelog, Instant datePublished, int downloads, String versionType, List<ModVersionFile> files, List<String> dependencies, List<String> gameVersions, List<String> loaders) {
            this.id = id;
            this.modId = modId;
            this.authorId = authorId;
            this.name = name;
            this.versionNumber = versionNumber;
            this.changelog = changelog;
            this.datePublished = datePublished;
            this.downloads = downloads;
            this.versionType = versionType;
            this.files = files;
            this.dependencies = dependencies;
            this.gameVersions = gameVersions;
            this.loaders = loaders;
        }

        public String getId() {
            return id;
        }

        public String getModId() {
            return modId;
        }

        public String getAuthorId() {
            return authorId;
        }

        public String getName() {
            return name;
        }

        public String getVersionNumber() {
            return versionNumber;
        }

        public String getChangelog() {
            return changelog;
        }

        public Instant getDatePublished() {
            return datePublished;
        }

        public int getDownloads() {
            return downloads;
        }

        public String getVersionType() {
            return versionType;
        }

        public List<ModVersionFile> getFiles() {
            return files;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public List<String> getGameVersions() {
            return gameVersions;
        }

        public List<String> getLoaders() {
            return loaders;
        }

        @Override
        public RemoteMod.Type getType() {
            return RemoteMod.Type.MODRINTH;
        }

        public Optional<RemoteMod.Version> toVersion() {
            RemoteMod.VersionType type;
            if ("release".equals(versionType)) {
                type = RemoteMod.VersionType.Release;
            } else if ("beta".equals(versionType)) {
                type = RemoteMod.VersionType.Beta;
            } else if ("alpha".equals(versionType)) {
                type = RemoteMod.VersionType.Alpha;
            } else {
                type = RemoteMod.VersionType.Release;
            }

            if (files.size() == 0) {
                return Optional.empty();
            }

            return Optional.of(new RemoteMod.Version(
                    this,
                    modId,
                    name,
                    versionNumber,
                    changelog,
                    datePublished,
                    type,
                    files.get(0).toFile(),
                    dependencies,
                    gameVersions,
                    loaders.stream().flatMap(loader -> {
                        if ("fabric".equalsIgnoreCase(loader)) return Stream.of(ModLoaderType.FABRIC);
                        else if ("forge".equalsIgnoreCase(loader)) return Stream.of(ModLoaderType.FORGE);
                        else return Stream.empty();
                    }).collect(Collectors.toList())
            ));
        }
    }

    public static class ModVersionFile {
        private final Map<String, String> hashes;
        private final String url;
        private final String filename;

        public ModVersionFile(Map<String, String> hashes, String url, String filename) {
            this.hashes = hashes;
            this.url = url;
            this.filename = filename;
        }

        public Map<String, String> getHashes() {
            return hashes;
        }

        public String getUrl() {
            return url;
        }

        public String getFilename() {
            return filename;
        }

        public RemoteMod.File toFile() {
            return new RemoteMod.File(hashes, url, filename);
        }
    }

    public static class ModResult implements RemoteMod.IMod {
        @SerializedName("mod_id")
        private final String modId;

        private final String slug;

        private final String author;

        private final String title;

        private final String description;

        private final List<String> categories;

        private final List<String> versions;

        private final int downloads;

        @SerializedName("page_url")
        private final String pageUrl;

        @SerializedName("icon_url")
        private final String iconUrl;

        @SerializedName("author_url")
        private final String authorUrl;

        @SerializedName("date_created")
        private final Instant dateCreated;

        @SerializedName("date_modified")
        private final Instant dateModified;

        @SerializedName("latest_version")
        private final String latestVersion;

        public ModResult(String modId, String slug, String author, String title, String description, List<String> categories, List<String> versions, int downloads, String pageUrl, String iconUrl, String authorUrl, Instant dateCreated, Instant dateModified, String latestVersion) {
            this.modId = modId;
            this.slug = slug;
            this.author = author;
            this.title = title;
            this.description = description;
            this.categories = categories;
            this.versions = versions;
            this.downloads = downloads;
            this.pageUrl = pageUrl;
            this.iconUrl = iconUrl;
            this.authorUrl = authorUrl;
            this.dateCreated = dateCreated;
            this.dateModified = dateModified;
            this.latestVersion = latestVersion;
        }

        public String getModId() {
            return modId;
        }

        public String getSlug() {
            return slug;
        }

        public String getAuthor() {
            return author;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getCategories() {
            return categories;
        }

        public List<String> getVersions() {
            return versions;
        }

        public int getDownloads() {
            return downloads;
        }

        public String getPageUrl() {
            return pageUrl;
        }

        public String getIconUrl() {
            return iconUrl;
        }

        public String getAuthorUrl() {
            return authorUrl;
        }

        public Instant getDateCreated() {
            return dateCreated;
        }

        public Instant getDateModified() {
            return dateModified;
        }

        public String getLatestVersion() {
            return latestVersion;
        }

        @Override
        public List<RemoteMod> loadDependencies() throws IOException {
            return Collections.emptyList();
        }

        @Override
        public Stream<RemoteMod.Version> loadVersions() throws IOException {
            return ModrinthRemoteModRepository.INSTANCE.getRemoteVersionsById(getModId());
        }

        public RemoteMod toMod() {
            return new RemoteMod(
                    slug,
                    author,
                    title,
                    description,
                    categories,
                    pageUrl,
                    iconUrl,
                    this
            );
        }
    }

    public static class Response<T> {
        private final int offset;

        private final int limit;

        @SerializedName("total_hits")
        private final int totalHits;

        private final List<T> hits;

        public Response() {
            this(0, 0, Collections.emptyList());
        }

        public Response(int offset, int limit, List<T> hits) {
            this.offset = offset;
            this.limit = limit;
            this.totalHits = hits.size();
            this.hits = hits;
        }

        public int getOffset() {
            return offset;
        }

        public int getLimit() {
            return limit;
        }

        public int getTotalHits() {
            return totalHits;
        }

        public List<T> getHits() {
            return hits;
        }
    }
}
