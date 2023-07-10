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
package org.jackhuang.hmcl.mod.impl.modrinth;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

public final class ModrinthRemoteModRepository implements RemoteModRepository {
    public static final ModrinthRemoteModRepository MODS = new ModrinthRemoteModRepository("mod");
    public static final ModrinthRemoteModRepository MODPACKS = new ModrinthRemoteModRepository("modpack");
    public static final ModrinthRemoteModRepository RESOURCE_PACKS = new ModrinthRemoteModRepository("resourcepack");

    private static final String PREFIX = "https://api.modrinth.com";

    private final String projectType;

    private ModrinthRemoteModRepository(String projectType) {
        this.projectType = projectType;
    }

    @Override
    public Type getType() {
        return Type.MOD;
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

    @Override
    public Stream<RemoteMod> search(String gameVersion, @Nullable RemoteModRepository.Category category, int pageOffset, int pageSize, String searchFilter, SortType sort, SortOrder sortOrder) throws IOException {
        List<List<String>> facets = new ArrayList<>();
        facets.add(Collections.singletonList("project_type:" + projectType));
        if (StringUtils.isNotBlank(gameVersion)) {
            facets.add(Collections.singletonList("versions:" + gameVersion));
        }
        if (category != null && StringUtils.isNotBlank(category.getId())) {
            facets.add(Collections.singletonList("categories:" + category.getId()));
        }
        Map<String, String> query = mapOf(
                pair("query", searchFilter),
                pair("facets", JsonUtils.UGLY_GSON.toJson(facets)),
                pair("offset", Integer.toString(pageOffset)),
                pair("limit", Integer.toString(pageSize)),
                pair("index", convertSortType(sort))
        );
        Response<ProjectSearchResult> response = HttpRequest.GET(NetworkUtils.withQuery(PREFIX + "/v2/search", query))
                .getJson(new TypeToken<Response<ProjectSearchResult>>() {
                }.getType());
        return response.getHits().stream().map(ProjectSearchResult::toMod);
    }

    @Override
    public Optional<RemoteMod.Version> getRemoteVersionByLocalFile(LocalModFile localModFile, Path file) throws IOException {
        String sha1 = DigestUtils.digestToString("SHA-1", file);

        try {
            ModrinthMod.ModrinthVersionImpl mod = HttpRequest.GET(PREFIX + "/v2/version_file/" + sha1,
                            pair("algorithm", "sha1"))
                    .getJson(ModrinthMod.ModrinthVersionImpl.class);
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
    public RemoteMod getModById(String id) throws IOException {
        id = StringUtils.removePrefix(id, "local-");
        ModrinthMod modrinthMod = HttpRequest.GET(PREFIX + "/v2/modrinthMod/" + id).getJson(ModrinthMod.class);
        return modrinthMod.toMod();
    }

    @Override
    public RemoteMod.File getModFile(String modId, String fileId) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<RemoteMod.Version> getRemoteVersionsById(String id) throws IOException {
        id = StringUtils.removePrefix(id, "local-");
        List<ModrinthMod.ModrinthVersionImpl> versions = HttpRequest.GET(PREFIX + "/v2/project/" + id + "/version")
                .getJson(new TypeToken<List<ModrinthMod.ModrinthVersionImpl>>() {
                }.getType());
        return versions.stream().map(ModrinthMod.ModrinthVersionImpl::toVersion).flatMap(Lang::toStream);
    }

    public List<Category> getCategoriesImpl() throws IOException {
        List<Category> categories = HttpRequest.GET(PREFIX + "/v2/tag/category").getJson(new TypeToken<List<Category>>() {}.getType());
        return categories.stream().filter(category -> category.getProjectType().equals(projectType)).collect(Collectors.toList());
    }

    @Override
    public Stream<RemoteModRepository.Category> getCategories() throws IOException {
        return getCategoriesImpl().stream().map(Category::toCategory);
    }

    public static class Category {
        private final String icon;

        private final String name;

        @SerializedName("project_type")
        private final String projectType;

        public Category() {
            this("","","");
        }

        public Category(String icon, String name, String projectType) {
            this.icon = icon;
            this.name = name;
            this.projectType = projectType;
        }

        public String getIcon() {
            return icon;
        }

        public String getName() {
            return name;
        }

        public String getProjectType() {
            return projectType;
        }

        public RemoteModRepository.Category toCategory() {
            return new RemoteModRepository.Category(
                    this,
                    name,
                    Collections.emptyList());
        }
    }

    @Immutable
    public static class Dependency {
        @SerializedName("version_id")
        private final String versionId;

        @SerializedName("project_id")
        private final String projectId;

        @SerializedName("dependency_type")
        private final String dependencyType;

        public Dependency(String versionId, String projectId, String dependencyType) {
            this.versionId = versionId;
            this.projectId = projectId;
            this.dependencyType = dependencyType;
        }

        public String getVersionId() {
            return versionId;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getDependencyType() {
            return dependencyType;
        }
    }

    public static class ProjectVersionFile {
        private final Map<String, String> hashes;
        private final String url;
        private final String filename;
        private final boolean primary;
        private final int size;

        public ProjectVersionFile(Map<String, String> hashes, String url, String filename, boolean primary, int size) {
            this.hashes = hashes;
            this.url = url;
            this.filename = filename;
            this.primary = primary;
            this.size = size;
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

        public boolean isPrimary() {
            return primary;
        }

        public int getSize() {
            return size;
        }

        public RemoteMod.File toFile() {
            return new RemoteMod.File(hashes, url, filename);
        }
    }

    public static class ProjectSearchResult implements RemoteMod.IMod {
        private final String slug;

        private final String title;

        private final String description;

        private final List<String> categories;

        @SerializedName("project_type")
        private final String projectType;

        private final int downloads;

        @SerializedName("icon_url")
        private final String iconUrl;

        @SerializedName("project_id")
        private final String projectId;

        private final String author;

        private final List<String> versions;

        @SerializedName("date_created")
        private final Date dateCreated;

        @SerializedName("date_modified")
        private final Date dateModified;

        @SerializedName("latest_version")
        private final String latestVersion;

        public ProjectSearchResult(String slug, String title, String description, List<String> categories, String projectType, int downloads, String iconUrl, String projectId, String author, List<String> versions, Date dateCreated, Date dateModified, String latestVersion) {
            this.slug = slug;
            this.title = title;
            this.description = description;
            this.categories = categories;
            this.projectType = projectType;
            this.downloads = downloads;
            this.iconUrl = iconUrl;
            this.projectId = projectId;
            this.author = author;
            this.versions = versions;
            this.dateCreated = dateCreated;
            this.dateModified = dateModified;
            this.latestVersion = latestVersion;
        }

        public String getSlug() {
            return slug;
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

        public String getProjectType() {
            return projectType;
        }

        public int getDownloads() {
            return downloads;
        }

        public String getIconUrl() {
            return iconUrl;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getAuthor() {
            return author;
        }

        public List<String> getVersions() {
            return versions;
        }

        public Date getDateCreated() {
            return dateCreated;
        }

        public Date getDateModified() {
            return dateModified;
        }

        public String getLatestVersion() {
            return latestVersion;
        }

        @Override
        public List<RemoteMod> loadDependencies(RemoteModRepository modRepository) throws IOException {
            Set<String> dependencies = modRepository.getRemoteVersionsById(getProjectId())
                    .flatMap(version -> version.getDependencies().stream())
                    .collect(Collectors.toSet());
            List<RemoteMod> mods = new ArrayList<>();
            for (String dependencyId : dependencies) {
                if (StringUtils.isNotBlank(dependencyId)) {
                    mods.add(modRepository.getModById(dependencyId));
                }
            }
            return mods;
        }

        @Override
        public Stream<RemoteMod.Version> loadVersions(RemoteModRepository modRepository) throws IOException {
            return modRepository.getRemoteVersionsById(getProjectId());
        }

        public RemoteMod toMod() {
            return new RemoteMod(
                    slug,
                    author,
                    title,
                    description,
                    categories,
                    String.format("https://modrinth.com/%s/%s", projectType, projectId),
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
