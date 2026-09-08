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
package org.jackhuang.hmcl.addon.repository;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.addon.AddonLoader;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.PriorityComparator;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.NoCandidatesException;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @see <a href="https://docs.modrinth.com/api">Modrinth API Doc</a>
public final class ModrinthRemoteAddonRepository implements RemoteAddonRepository {
    public static final ModrinthRemoteAddonRepository COMMON = new ModrinthRemoteAddonRepository();
    public static final ModrinthRemoteAddonRepository MODS = new ModrinthRemoteAddonRepository("mod");
    public static final ModrinthRemoteAddonRepository MODPACKS = new ModrinthRemoteAddonRepository("modpack");
    public static final ModrinthRemoteAddonRepository RESOURCE_PACKS = new ModrinthRemoteAddonRepository("resourcepack");
    public static final ModrinthRemoteAddonRepository SHADER_PACKS = new ModrinthRemoteAddonRepository("shader");

    private static final Comparator<String> TAG_COMPARATOR = PriorityComparator.of(
            List.of("babric",
                    "bta-babric",
                    "bukkit",
                    "bungeecord",
                    "canvas",
                    "datapack",
                    "fabric",
                    "folia",
                    "forge",
                    "geyser",
                    "iris",
                    "java-agent",
                    "legacy-fabric",
                    "liteloader",
                    "minecraft",
                    "modloader",
                    "mrpack",
                    "neoforge",
                    "nilloader",
                    "optifine",
                    "ornith",
                    "paper",
                    "purpur",
                    "quilt",
                    "rift",
                    "spigot",
                    "sponge",
                    "vanilla",
                    "velocity",
                    "waterfall"),
            Comparator.naturalOrder(),
            false
    );

    @Nullable
    private static RemoteAddon.Type toAddonType(String projectType) {
        return switch (projectType) {
            case "modpack" -> RemoteAddon.Type.MODPACK;
            case "resourcepack" -> RemoteAddon.Type.RESOURCE_PACK;
            case "shader" -> RemoteAddon.Type.SHADER_PACK;
            case "mod" -> RemoteAddon.Type.MOD;
            default -> null;
        };
    }

    private static final Semaphore SEMAPHORE = new Semaphore(16);

    private static final String PREFIX = "https://api.modrinth.com";

    private static final String BASE = "https://modrinth.com";

    private final @Nullable String projectType;

    private final @Nullable RemoteAddon.Type type;

    private ModrinthRemoteAddonRepository() {
        this.projectType = null;
        this.type = null;
    }

    private ModrinthRemoteAddonRepository(@NotNull String projectType) {
        this.projectType = projectType;
        this.type = toAddonType(projectType);
        if (type == null) throw new IllegalArgumentException("Unsupported Modrinth project type: " + projectType);
    }

    @Override
    public RemoteAddon.Type getType() {
        if (type == null) throw new UnsupportedOperationException();
        return this.type;
    }

    @Override
    public String getApiBaseUrl() {
        return PREFIX;
    }

    @Override
    public String getBaseUrl() {
        return BASE;
    }

    private static String convertSortType(SortType sortType) {
        return switch (sortType) {
            case DATE_CREATED -> "newest";
            case POPULARITY, NAME, AUTHOR -> "relevance";
            case LAST_UPDATED -> "updated";
            case TOTAL_DOWNLOADS -> "downloads";
            default -> throw new IllegalArgumentException("Unsupported sort type " + sortType);
        };
    }

    @Unmodifiable
    private static List<String> sortDisplayCategories(@Nullable List<String> displayCategories) {
        return displayCategories != null && !displayCategories.isEmpty()
                ? displayCategories.stream().sorted(TAG_COMPARATOR).toList()
                : List.of();
    }

    @Override
    public SearchResult search(DownloadProvider downloadProvider, String gameVersion, @Nullable RemoteAddonRepository.Category category, int pageOffset, int pageSize, String searchFilter, SortType sort, SortOrder sortOrder) throws IOException {
        if (projectType == null) throw new UnsupportedOperationException();
        SEMAPHORE.acquireUninterruptibly();
        try {
            List<List<String>> facets = new ArrayList<>();
            facets.add(Collections.singletonList("project_type:" + projectType));
            if (StringUtils.isNotBlank(gameVersion)) {
                facets.add(Collections.singletonList("versions:" + gameVersion));
            }
            if (category != null && StringUtils.isNotBlank(category.id())) {
                facets.add(Collections.singletonList("categories:" + category.id()));
            }
            Map<String, String> query = mapOf(
                    pair("query", searchFilter),
                    pair("facets", JsonUtils.UGLY_GSON.toJson(facets)),
                    pair("offset", Integer.toString(pageOffset * pageSize)),
                    pair("limit", Integer.toString(pageSize)),
                    pair("index", convertSortType(sort))
            );

            List<URI> candidates = downloadProvider.injectURLWithCandidates(NetworkUtils.withQuery(PREFIX + "/v2/search", query));
            IOException exception = null;
            for (URI candidate : candidates) {
                try {
                    LOG.info("Fetching " + candidate);
                    Response<ProjectSearchResult> response = HttpRequest.GET(candidate.toString())
                            .getJson(Response.typeOf(ProjectSearchResult.class));
                    return new SearchResult(response.getHits().stream().map(ProjectSearchResult::toAddon), (int) Math.ceil((double) response.totalHits / pageSize));
                } catch (IOException e) {
                    LOG.warning("Failed to search addons: " + candidate, e);

                    IOException wrapper = new IOException("Failed to search addons: " + candidate, e);
                    if (candidates.size() == 1) {
                        exception = wrapper;
                    } else {
                        if (exception == null) {
                            exception = new IOException("Failed to search addons");
                        }
                        exception.addSuppressed(wrapper);
                    }
                }
            }

            throw exception != null ? exception : new NoCandidatesException();
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public Optional<RemoteAddon.Version> getRemoteVersionByLocalFile(Path file) throws IOException {
        String sha1 = DigestUtils.digestToString("SHA-1", file);

        SEMAPHORE.acquireUninterruptibly();
        try {
            ProjectVersion projectVersion = HttpRequest.GET(PREFIX + "/v2/version_file/" + sha1,
                            pair("algorithm", "sha1"))
                    .getJson(ProjectVersion.class);
            return projectVersion.toVersion();
        } catch (ResponseCodeException e) {
            if (e.getResponseCode() == 404) {
                return Optional.empty();
            } else {
                throw e;
            }
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public RemoteAddon getAddonById(DownloadProvider downloadProvider, String id) throws IOException {
        SEMAPHORE.acquireUninterruptibly();
        try {
            id = StringUtils.removePrefix(id, "local-");
            List<URI> candidates = downloadProvider.injectURLWithCandidates(PREFIX + "/v2/project/" + id);
            IOException exception = null;

            for (URI candidate : candidates) {
                try {
                    Project project = HttpRequest.GET(candidate.toString()).getJson(Project.class);
                    return project.toAddon();
                } catch (IOException e) {
                    IOException wrapper = new IOException("Failed to get addon: " + candidate, e);
                    if (candidates.size() == 1) {
                        exception = wrapper;
                    } else {
                        if (exception == null) {
                            exception = new IOException("Failed to get addon");
                        }
                        exception.addSuppressed(wrapper);
                    }
                }
            }

            throw exception != null ? exception : new NoCandidatesException();
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public RemoteAddon resolveDependency(DownloadProvider downloadProvider, String id) throws IOException {
        try {
            return getAddonById(downloadProvider, id);
        } catch (IOException e) {
            if (e instanceof NoCandidatesException) throw e;
            List<Throwable> l = new ArrayList<>(List.of(e));
            l.addAll(List.of(e.getSuppressed()));
            if (l.stream().allMatch(t -> {
                var cause = t.getCause();
                return cause == null
                        || cause instanceof FileNotFoundException
                        || cause instanceof ResponseCodeException rce && rce.getResponseCode() == 404;
            })) { // Which means the file does not exist
                return RemoteAddon.BROKEN;
            }
            throw e;
        }
    }

    @Override
    public RemoteAddon.File getAddonFile(String projectId, String fileId) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<RemoteAddon.Version> getRemoteVersionsById(DownloadProvider downloadProvider, String id) throws IOException {
        SEMAPHORE.acquireUninterruptibly();
        try {
            id = StringUtils.removePrefix(id, "local-");

            List<URI> candidates = downloadProvider.injectURLWithCandidates(PREFIX + "/v2/project/" + id + "/version?include_changelog=false");
            IOException exception = null;

            for (URI candidate : candidates) {
                try {
                    List<ProjectVersion> versions = HttpRequest.GET(candidate.toString())
                            .getJson(listTypeOf(ProjectVersion.class));
                    return versions.stream().map(ProjectVersion::toVersion).flatMap(Optional::stream);
                } catch (IOException e) {
                    IOException wrapper = new IOException("Failed to get remote versions: " + candidate, e);
                    if (candidates.size() == 1) {
                        exception = wrapper;
                    } else {
                        if (exception == null) {
                            exception = new IOException("Failed to get remote versions");
                        }
                        exception.addSuppressed(wrapper);
                    }
                }
            }

            throw exception != null ? exception : new NoCandidatesException();
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public String getAddonChangelog(DownloadProvider downloadProvider, String addonId, String versionId) throws IOException {
        SEMAPHORE.acquireUninterruptibly();
        try {
            List<URI> candidates = downloadProvider.injectURLWithCandidates(PREFIX + "/v2/version/" + versionId);
            IOException exception = null;

            for (URI uri : candidates) {
                try {
                    ProjectVersion version = HttpRequest.GET(uri.toString()).getJson(ProjectVersion.class);
                    return version.changelog();
                } catch (IOException e) {
                    IOException wrapper = new IOException("Failed to get addon changelog: " + uri, e);
                    if (candidates.size() == 1) {
                        exception = wrapper;
                    } else {
                        if (exception == null) {
                            exception = new IOException("Failed to get addon changelog");
                        }
                        exception.addSuppressed(wrapper);
                    }
                }
            }

            throw exception != null ? exception : new IOException("No candidates found");
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public @NotNull String getVersionPageUrl(RemoteAddon.Version version) {
        return "%s/project/%s/version/%s".formatted(BASE, version.projectId(), version.versionId()); // Modrinth will help us redirect
    }

    @Override
    public Stream<RemoteAddonRepository.Category> getCategories() throws IOException {
        if (projectType == null) throw new UnsupportedOperationException();
        SEMAPHORE.acquireUninterruptibly();
        try {
            List<Category> categories = HttpRequest.GET(PREFIX + "/v2/tag/category").getJson(listTypeOf(Category.class));
            return categories.stream()
                    .filter(category -> category.projectType().equals(projectType))
                    .map(Category::toCategory);
        } finally {
            SEMAPHORE.release();
        }
    }

    @JsonSerializable
    public record Category(String icon, String name, @SerializedName("project_type") String projectType) {
        public Category() {
            this("", "", "");
        }

        public RemoteAddonRepository.Category toCategory() {
            return new RemoteAddonRepository.Category(
                    this,
                    name,
                    Collections.emptyList());
        }
    }

    /**
     * @param body A long body describing project in detail.
     */
    public record Project(String slug, String title, String description, List<String> categories, String body,
                          @SerializedName("project_type") String projectType, int downloads,
                          @SerializedName("icon_url") String iconUrl, String id, String team, Instant published,
                          Instant updated, List<String> versions) implements RemoteAddon.IAddon {

        @Override
        public List<RemoteAddon> loadDependencies(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            Set<RemoteAddon.Dependency> dependencies = repo.getRemoteVersionsById(downloadProvider, id())
                    .flatMap(version -> version.dependencies().stream())
                    .collect(Collectors.toSet());
            List<RemoteAddon> addons = new ArrayList<>();
            for (RemoteAddon.Dependency dependency : dependencies) {
                addons.add(dependency.load(downloadProvider));
            }
            return addons;
        }

        @Override
        public Stream<RemoteAddon.Version> loadVersions(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            return repo.getRemoteVersionsById(downloadProvider, id());
        }

        public RemoteAddon toAddon() {
            return new RemoteAddon(
                    slug,
                    "",
                    title,
                    description,
                    categories,
                    String.format("https://modrinth.com/%s/%s", projectType, id),
                    iconUrl,
                    this,
                    toAddonType(projectType)
            );
        }
    }

    @Immutable
    public record Dependency(@SerializedName("version_id") String versionId,
                             @SerializedName("project_id") String projectId,
                             @SerializedName("dependency_type") String dependencyType) {
    }

    public record ProjectVersion(String name, @SerializedName("version_number") String versionNumber, String changelog,
                                 List<Dependency> dependencies,
                                 @SerializedName("game_versions") List<String> gameVersions,
                                 @SerializedName("version_type") String versionType, List<String> loaders,
                                 boolean featured, String id, @SerializedName("project_id") String projectId,
                                 @SerializedName("author_id") String authorId,
                                 @SerializedName("date_published") Instant datePublished, int downloads,
                                 @SerializedName("changelog_url") String changelogUrl,
                                 List<ProjectVersionFile> files) implements RemoteAddon.IVersion {
        private static final Map<String, RemoteAddon.@Nullable DependencyType> DEPENDENCY_TYPE = mapOf(
                pair("required", RemoteAddon.DependencyType.REQUIRED),
                pair("optional", RemoteAddon.DependencyType.OPTIONAL),
                pair("embedded", RemoteAddon.DependencyType.EMBEDDED),
                pair("incompatible", RemoteAddon.DependencyType.INCOMPATIBLE)
        );

        @Override
        public RemoteAddon.Source getSource() {
            return RemoteAddon.Source.MODRINTH;
        }

        public Optional<RemoteAddon.Version> toVersion() {
            RemoteAddon.VersionType type;
            if ("release".equals(versionType)) {
                type = RemoteAddon.VersionType.Release;
            } else if ("beta".equals(versionType)) {
                type = RemoteAddon.VersionType.Beta;
            } else if ("alpha".equals(versionType)) {
                type = RemoteAddon.VersionType.Alpha;
            } else {
                type = RemoteAddon.VersionType.Release;
            }

            if (files.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new RemoteAddon.Version(
                    this,
                    id,
                    projectId,
                    name,
                    versionNumber,
                    datePublished,
                    type,
                    files.get(0).toFile(),
                    dependencies.stream().map(dependency -> {
                        if (dependency.projectId == null) {
                            return RemoteAddon.Dependency.ofBroken();
                        }

                        if (!DEPENDENCY_TYPE.containsKey(dependency.dependencyType)) {
                            throw new IllegalStateException("Broken datas");
                        }

                        return RemoteAddon.Dependency.ofGeneral(DEPENDENCY_TYPE.get(dependency.dependencyType), RemoteAddon.Source.MODRINTH, dependency.projectId);
                    }).filter(Objects::nonNull).collect(Collectors.toList()),
                    gameVersions,
                    loaders.stream().map(AddonLoader::of).toList()
            ));
        }
    }

    public record ProjectVersionFile(Map<String, String> hashes, String url, String filename, boolean primary,
                                     int size) {

        public RemoteAddon.File toFile() {
            return new RemoteAddon.File(hashes, url, filename);
        }
    }

    public record ProjectSearchResult(String slug, String title, String description, List<String> categories,
                                      @SerializedName("display_categories") List<String> displayCategories,
                                      @SerializedName("project_type") String projectType, int downloads,
                                      @SerializedName("icon_url") String iconUrl,
                                      @SerializedName("project_id") String projectId, String author,
                                      List<String> versions, @SerializedName("date_created") Instant dateCreated,
                                      @SerializedName("date_modified") Instant dateModified,
                                      @SerializedName("latest_version") String latestVersion) implements RemoteAddon.IAddon {

        @Override
        public List<RemoteAddon> loadDependencies(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            Set<RemoteAddon.Dependency> dependencies = repo.getRemoteVersionsById(downloadProvider, projectId())
                    .flatMap(version -> version.dependencies().stream())
                    .collect(Collectors.toSet());
            List<RemoteAddon> addons = new ArrayList<>();
            for (RemoteAddon.Dependency dependency : dependencies) {
                addons.add(dependency.load(downloadProvider));
            }
            return addons;
        }

        @Override
        public Stream<RemoteAddon.Version> loadVersions(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            return repo.getRemoteVersionsById(downloadProvider, projectId());
        }

        public RemoteAddon toAddon() {
            return new RemoteAddon(
                    slug,
                    author,
                    title,
                    description,
                    sortDisplayCategories(displayCategories),
                    String.format("https://modrinth.com/%s/%s", projectType, projectId),
                    iconUrl,
                    this,
                    toAddonType(projectType)
            );
        }
    }

    public static class Response<T> {

        @SuppressWarnings("unchecked")
        public static <T> TypeToken<Response<T>> typeOf(Class<T> responseType) {
            return (TypeToken<Response<T>>) TypeToken.getParameterized(Response.class, responseType);
        }

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
