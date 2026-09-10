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

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.addon.AddonLoader;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.MurmurHash2;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.*;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Checksum;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @see <a href="https://docs.curseforge.com/rest-api">CurseForge API Doc</a>
public final class CurseForgeRemoteAddonRepository implements RemoteAddonRepository {

    private static final String PREFIX = "https://api.curseforge.com";
    private static final String BASE = "https://www.curseforge.com";
    private static final Semaphore SEMAPHORE = new Semaphore(16);

    public static final String API_KEY = System.getProperty("hmcl.curseforge.apikey", JarUtils.getAttribute("hmcl.curseforge.apikey", ""));

    private static <R extends HttpRequest> R withApiKey(R request) {
        if (request.getUrl().startsWith(PREFIX) && !API_KEY.isEmpty()) {
            request.header("X-API-KEY", API_KEY);
        }
        return request;
    }

    public static boolean isAvailable() {
        return !API_KEY.isEmpty();
    }

    private final @Nullable RemoteAddon.Type type;
    private final int section;

    public CurseForgeRemoteAddonRepository() {
        this.type = null;
        this.section = -1;
    }

    public CurseForgeRemoteAddonRepository(int section) {
        this.type = toAddonType(section);
        if (type == null) throw new IllegalArgumentException("Unsupported CurseForge section id: " + section);
        this.section = section;
    }

    @Override
    public RemoteAddon.Type getType() {
        if (type == null) throw new UnsupportedOperationException();
        return type;
    }

    @Override
    public String getApiBaseUrl() {
        return PREFIX;
    }

    @Override
    public String getBaseUrl() {
        return BASE;
    }

    private static int toModsSearchSortField(SortType sort) {
        // https://docs.curseforge.com/rest-api/#tocS_ModsSearchSortField
        return switch (sort) {
            case RELEVANCY -> 1; // This represents Featured, however test shows that they are quite similar
            case POPULARITY -> 2;
            case DATE_CREATED -> 11;
            case LAST_UPDATED -> 3;
            case TOTAL_DOWNLOADS -> 6;
        };
    }

    private static String toSortOrder(SortOrder sortOrder) {
        // https://docs.curseforge.com/rest-api/#tocS_SortOrder
        return switch (sortOrder) {
            case ASC -> "asc";
            case DESC -> "desc";
        };
    }

    private static int calculateTotalPages(Response<List<CurseAddon>> response, int pageSize) {
        return (int) Math.ceil((double) Math.min(response.pagination.totalCount, 10000) / pageSize);
    }

    /// Searches the configured section, preserving the order returned by the server.
    ///
    /// @throws UnsupportedOperationException if this repository has no configured section
    /// @throws NoCandidatesException if no response is obtained and no I/O failure was recorded
    /// @throws IOException if all candidate requests fail with I/O errors
    @Override
    public SearchResult search(DownloadProvider downloadProvider, String gameVersion, @Nullable RemoteAddonRepository.Category category, int pageOffset, int pageSize, String searchFilter, SortType sortType, SortOrder sortOrder) throws IOException {
        if (type == null) throw new UnsupportedOperationException();
        SEMAPHORE.acquireUninterruptibly();
        try {
            int categoryId = 0;
            if (category != null && category.self() instanceof Category) {
                categoryId = ((Category) category.self()).getId();
            }

            var query = new LinkedHashMap<String, String>();
            query.put("gameId", "432");
            query.put("classId", Integer.toString(section));
            if (categoryId != 0)
                query.put("categoryId", Integer.toString(categoryId));
            query.put("gameVersion", gameVersion);
            query.put("searchFilter", searchFilter);
            query.put("sortField", Integer.toString(toModsSearchSortField(sortType)));
            query.put("sortOrder", toSortOrder(sortOrder));
            query.put("index", Integer.toString(pageOffset * pageSize));
            query.put("pageSize", Integer.toString(pageSize));

            @Nullable Response<List<CurseAddon>> response = null;

            @Nullable IOException exception = null;
            List<URI> candidates = downloadProvider.injectURLWithCandidates(NetworkUtils.withQuery(PREFIX + "/v1/mods/search", query));
            for (URI candidate : candidates) {
                LOG.info("Fetching " + candidate);
                try {
                    response = withApiKey(HttpRequest.GET(candidate.toString()))
                            .getJson(Response.typeOf(listTypeOf(CurseAddon.class)));
                    break;
                } catch (IOException e) {
                    LOG.warning("Failed to search addons: " + candidate, e);
                    if (candidates.size() == 1) {
                        exception = e;
                    } else {
                        if (exception == null) {
                            exception = new IOException("Failed to search addons");
                        }
                        exception.addSuppressed(e);
                    }
                }
            }

            if (response != null)
                return new SearchResult(response.data().stream().map(CurseAddon::toAddon), calculateTotalPages(response, pageSize));

            throw exception != null ? exception : new NoCandidatesException();
        } finally {
            SEMAPHORE.release();
        }
    }

    /// Calculates the CurseForge fingerprint without retaining the filtered file in memory.
    static long calculateFingerprint(Path file) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            long startPosition = channel.position();

            byte[] bufferArray = new byte[1024 * 1024];
            ByteBuffer buffer = ByteBuffer.wrap(bufferArray);

            long filteredLength = 0;
            while (channel.read(buffer) > 0) {
                int len = buffer.position();
                for (int i = 0; i < len; i++) {
                    byte b = bufferArray[i];
                    if (b != 0x9 && b != 0xa && b != 0xd && b != 0x20) {
                        filteredLength++;
                    }
                }
                buffer.clear();
            }

            channel.position(startPosition);

            Checksum hasher = MurmurHash2.hash32(filteredLength, 1);
            while (channel.read(buffer) > 0) {
                int len = buffer.position();

                int pos = 0;
                while (pos < len) {
                    byte b = bufferArray[pos];
                    if (b == 0x9 || b == 0xa || b == 0xd || b == 0x20) {
                        break;
                    }
                    pos++;
                }

                if (pos < len) {
                    int pos2 = pos + 1;
                    while (pos2 < len) {
                        byte b = bufferArray[pos2];
                        if (b != 0x9 && b != 0xa && b != 0xd && b != 0x20) {
                            bufferArray[pos++] = b;
                        }
                        pos2++;
                    }
                }

                hasher.update(bufferArray, 0, pos);
                buffer.clear();
            }
            return hasher.getValue();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IOException(e);
        }
    }

    /// Finds the remote CurseForge version matching a local file.
    @Override
    public Optional<RemoteAddon.Version> getRemoteVersionByLocalFile(Path file) throws IOException {
        long hash = calculateFingerprint(file);
        if (hash == 811513880) { // Workaround for https://github.com/HMCL-dev/HMCL/issues/4597
            return Optional.empty();
        }

        SEMAPHORE.acquireUninterruptibly();
        try {
            Response<FingerprintMatchesResult> response = withApiKey(HttpRequest.POST(PREFIX + "/v1/fingerprints/432"))
                    .json(mapOf(pair("fingerprints", Collections.singletonList(hash))))
                    .getJson(Response.typeOf(FingerprintMatchesResult.class));

            if (response.data().exactMatches() == null || response.data().exactMatches().isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(response.data().exactMatches().get(0).file().toVersion());
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public RemoteAddon getAddonById(DownloadProvider downloadProvider, String id) throws IOException {
        SEMAPHORE.acquireUninterruptibly();
        try {
            Response<CurseAddon> response = withApiKey(HttpRequest.GET(PREFIX + "/v1/mods/" + id))
                    .getJson(Response.typeOf(CurseAddon.class));
            return response.data.toAddon();
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
            if (e instanceof FileNotFoundException
                    || e instanceof ResponseCodeException rce && rce.getResponseCode() == 404)
                return RemoteAddon.BROKEN;
            throw e;
        }
    }

    @Override
    public RemoteAddon.File getAddonFile(String projectId, String fileId) throws IOException {
        SEMAPHORE.acquireUninterruptibly();
        try {
            Response<CurseAddon.LatestFile> response = withApiKey(HttpRequest.GET(String.format("%s/v1/mods/%s/files/%s", PREFIX, projectId, fileId)))
                    .getJson(Response.typeOf(CurseAddon.LatestFile.class));
            return response.data().toVersion().file();
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public Stream<RemoteAddon.Version> getRemoteVersionsById(DownloadProvider downloadProvider, String id) throws IOException {
        SEMAPHORE.acquireUninterruptibly();
        try {
            Response<List<CurseAddon.LatestFile>> response = withApiKey(HttpRequest.GET(PREFIX + "/v1/mods/" + id + "/files",
                    pair("pageSize", "10000")))
                    .getJson(Response.typeOf(listTypeOf(CurseAddon.LatestFile.class)));
            return response.data().stream().map(CurseAddon.LatestFile::toVersion);
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public String getAddonChangelog(DownloadProvider downloadProvider, String addonId, String versionId) throws IOException {
        SEMAPHORE.acquireUninterruptibly();
        try {
            Response<String> response = withApiKey(HttpRequest.GET(String.format("%s/v1/mods/%s/files/%s/changelog", PREFIX, addonId, versionId)))
                    .getJson(Response.typeOf(String.class));
            return response.data();
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public @NotNull String getVersionPageUrl(RemoteAddon.Version version) throws IOException {
        SEMAPHORE.acquireUninterruptibly();
        try {
            Response<CurseAddon> response = withApiKey(HttpRequest.GET(PREFIX + "/v1/mods/" + version.projectId()))
                    .getJson(Response.typeOf(CurseAddon.class));
            var addon = response.data();
            var classId = addon.classId();
            var clazz = switch (classId) {
                case SECTION_MOD -> "mc-mods";
                case SECTION_RESOURCE_PACK -> "texture-packs";
                case SECTION_WORLD -> "worlds";
                case SECTION_MODPACK -> "modpacks";
                case SECTION_DATAPACK -> "data-packs";
                case SECTION_BUKKIT_PLUGIN -> "bukkit-plugins";
                case SECTION_ADDONS -> "mc-addons";
                case SECTION_CUSTOMIZATION -> "customization";
                case SECTION_SHADER -> "shaders";
                default ->
                        throw new IllegalArgumentException("Unsupported CurseForge class id [%d]".formatted(classId));
            };
            return "%s/minecraft/%s/%s/files/%s".formatted(BASE, clazz, addon.slug(), version.versionId());
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public Stream<RemoteAddonRepository.Category> getCategories() throws IOException {
        if (type == null) throw new UnsupportedOperationException();
        SEMAPHORE.acquireUninterruptibly();
        try {
            Response<List<Category>> categories = withApiKey(HttpRequest.GET(PREFIX + "/v1/categories", pair("gameId", "432")))
                    .getJson(Response.typeOf(listTypeOf(Category.class)));
            return reorganizeCategories(categories.data(), section).stream().map(Category::toCategory);
        } finally {
            SEMAPHORE.release();
        }
    }

    private static List<Category> reorganizeCategories(List<Category> categories, int rootId) {
        List<Category> result = new ArrayList<>();

        Map<Integer, Category> categoryMap = new HashMap<>();
        for (Category category : categories) {
            categoryMap.put(category.getId(), category);
        }
        for (Category category : categories) {
            if (category.getParentCategoryId() == rootId) {
                result.add(category);
            } else {
                Category parentCategory = categoryMap.get(category.getParentCategoryId());
                if (parentCategory == null) {
                    // Category list is not correct, so we ignore this item.
                    continue;
                }
                parentCategory.getSubcategories().add(category);
            }
        }
        return result;
    }

    public static final int SECTION_BUKKIT_PLUGIN = 5;
    public static final int SECTION_MOD = 6;
    public static final int SECTION_RESOURCE_PACK = 12;
    public static final int SECTION_DATAPACK = 6945;
    public static final int SECTION_WORLD = 17;
    public static final int SECTION_MODPACK = 4471;
    public static final int SECTION_SHADER = 6552;
    public static final int SECTION_CUSTOMIZATION = 4546;
    public static final int SECTION_ADDONS = 4559; // For Pocket Edition
    public static final int SECTION_UNKNOWN1 = 4944;
    public static final int SECTION_UNKNOWN2 = 4979;
    public static final int SECTION_UNKNOWN3 = 4984;

    public static final CurseForgeRemoteAddonRepository COMMON = new CurseForgeRemoteAddonRepository();
    public static final CurseForgeRemoteAddonRepository MODS = new CurseForgeRemoteAddonRepository(SECTION_MOD);
    public static final CurseForgeRemoteAddonRepository MODPACKS = new CurseForgeRemoteAddonRepository(SECTION_MODPACK);
    public static final CurseForgeRemoteAddonRepository RESOURCE_PACKS = new CurseForgeRemoteAddonRepository(SECTION_RESOURCE_PACK);
    public static final CurseForgeRemoteAddonRepository WORLDS = new CurseForgeRemoteAddonRepository(SECTION_WORLD);
    public static final CurseForgeRemoteAddonRepository CUSTOMIZATIONS = new CurseForgeRemoteAddonRepository(SECTION_CUSTOMIZATION);
    public static final CurseForgeRemoteAddonRepository SHADERS = new CurseForgeRemoteAddonRepository(SECTION_SHADER);

    @Nullable
    private static RemoteAddon.Type toAddonType(int classId) {
        return switch (classId) {
            case SECTION_MODPACK -> RemoteAddon.Type.MODPACK;
            case SECTION_RESOURCE_PACK -> RemoteAddon.Type.RESOURCE_PACK;
            case SECTION_WORLD -> RemoteAddon.Type.WORLD;
            case SECTION_CUSTOMIZATION -> RemoteAddon.Type.CUSTOMIZATION;
            case SECTION_SHADER -> RemoteAddon.Type.SHADER_PACK;
            case SECTION_MOD -> RemoteAddon.Type.MOD;
            default -> null;
        };
    }

    public record Pagination(int index, int pageSize, int resultCount, int totalCount) {
    }

    public record Response<T>(T data, Pagination pagination) {

        @SuppressWarnings("unchecked")
        public static <T> TypeToken<Response<T>> typeOf(Class<T> responseType) {
            return (TypeToken<Response<T>>) TypeToken.getParameterized(Response.class, responseType);
        }

        @SuppressWarnings("unchecked")
        public static <T> TypeToken<Response<T>> typeOf(TypeToken<T> responseType) {
            return (TypeToken<Response<T>>) TypeToken.getParameterized(Response.class, responseType.getType());
        }

    }

    /**
     * @see <a href="https://docs.curseforge.com/rest-api/#tocS_FingerprintsMatchesResult">Schema</a>
     */
    private record FingerprintMatchesResult(boolean isCacheBuilt, List<FingerprintMatch> exactMatches,
                                            List<Long> exactFingerprints) {
    }

    /**
     * @see <a href="https://docs.curseforge.com/rest-api/#tocS_FingerprintMatch">Schema</a>
     */
    private record FingerprintMatch(int id, CurseAddon.LatestFile file, List<CurseAddon.LatestFile> latestFiles) {
    }

    @Immutable
    public record CurseAddon(int id, int gameId, String name, String slug, Links links, String summary,
                             int status,
                             int downloadCount, boolean isFeatured, int primaryCategoryId,
                             List<CurseForgeRemoteAddonRepository.Category> categories,
                             int classId, List<Author> authors, Logo logo, int mainFileId,
                             List<LatestFile> latestFiles,
                             List<LatestFileIndex> latestFileIndices, Instant dateCreated, Instant dateModified,
                             Instant dateReleased, boolean allowModDistribution, int gamePopularityRank,
                             boolean isAvailable, int thumbsUpCount) implements RemoteAddon.IAddon {
        public static final Map<Integer, RemoteAddon.DependencyType> RELATION_TYPE = mapOf(
                pair(1, RemoteAddon.DependencyType.EMBEDDED),
                pair(2, RemoteAddon.DependencyType.OPTIONAL),
                pair(3, RemoteAddon.DependencyType.REQUIRED),
                pair(4, RemoteAddon.DependencyType.TOOL),
                pair(5, RemoteAddon.DependencyType.INCOMPATIBLE),
                pair(6, RemoteAddon.DependencyType.INCLUDE)
        );

        @Override
        public List<RemoteAddon> loadDependencies(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            Set<Integer> dependencies = latestFiles.stream()
                    .flatMap(latestFile -> latestFile.dependencies().stream())
                    .filter(dep -> dep.relationType() == 3)
                    .map(Dependency::modId)
                    .collect(Collectors.toSet());
            List<RemoteAddon> mods = new ArrayList<>();
            for (int dependencyId : dependencies) {
                mods.add(repo.getAddonById(downloadProvider, Integer.toString(dependencyId)));
            }
            return mods;
        }

        @Override
        public Stream<RemoteAddon.Version> loadVersions(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            return repo.getRemoteVersionsById(downloadProvider, Integer.toString(id));
        }

        public RemoteAddon toAddon() {
            String iconUrl = "";
            if (logo != null) {
                if (StringUtils.isNotBlank(logo.thumbnailUrl()))
                    iconUrl = logo.thumbnailUrl();
                else if (StringUtils.isNotBlank(logo.url()))
                    iconUrl = logo.url();
            }

            return new RemoteAddon(
                    slug,
                    "",
                    name,
                    summary,
                    categories.stream().map(category -> Integer.toString(category.getId())).collect(Collectors.toList()),
                    links.websiteUrl,
                    iconUrl,
                    this,
                    toAddonType(classId)
            );
        }

        @Immutable
        public record Links(String websiteUrl, String wikiUrl, @Nullable String issuesUrl, @Nullable String sourceUrl) {
        }

        @Immutable
        public record Author(int id, String name, String url) {
        }

        @Immutable
        public record Logo(int id, int modId, String title, String description, String thumbnailUrl, String url) {
        }

        @Immutable
        public record Attachment(int id, int projectId, String description, boolean isDefault, String thumbnailUrl,
                                 String title, String url, int status) {
        }

        @Immutable
        public record Dependency(int modId, int relationType) {
            public Dependency() {
                this(0, 1);
            }
        }

        /**
         * @see <a href="https://docs.curseforge.com/rest-api/#tocS_FileHash">Schema</a>
         */
        @Immutable
        public record LatestFileHash(String value, int algo) {
        }

        /**
         * @see <a href="https://docs.curseforge.com/rest-api/#tocS_File">Schema</a>
         */
        @Immutable
        public record LatestFile(int id, int gameId, int modId, boolean isAvailable, String displayName,
                                 String fileName,
                                 int releaseType, int fileStatus, List<LatestFileHash> hashes, Instant fileDate,
                                 int fileLength, int downloadCount, String downloadUrl, List<String> gameVersions,
                                 List<Dependency> dependencies, int alternateFileId, boolean isServerPack,
                                 long fileFingerprint) implements RemoteAddon.IVersion {

            @Override
            public String downloadUrl() {
                if (downloadUrl == null) {
                    // This addon is not allowed for distribution, and downloadUrl will be null.
                    // We try to find its download url.
                    return String.format("https://edge.forgecdn.net/files/%d/%d/%s", id / 1000, id % 1000, fileName);
                }
                return downloadUrl;
            }

            @Override
            public RemoteAddon.Source getSource() {
                return RemoteAddon.Source.CURSEFORGE;
            }

            public RemoteAddon.Version toVersion() {
                RemoteAddon.VersionType versionType = switch (releaseType()) {
                    case 1 -> RemoteAddon.VersionType.Release;
                    case 2 -> RemoteAddon.VersionType.Beta;
                    case 3 -> RemoteAddon.VersionType.Alpha;
                    default -> RemoteAddon.VersionType.Release;
                };

                return new RemoteAddon.Version(
                        this,
                        Integer.toString(id()),
                        Integer.toString(modId()),
                        displayName(),
                        fileName(),
                        fileDate(),
                        versionType,
                        new RemoteAddon.File(Collections.emptyMap(), downloadUrl(), fileName()),
                        dependencies.stream().map(dependency -> {
                            if (!RELATION_TYPE.containsKey(dependency.relationType())) {
                                throw new IllegalStateException("Broken datas.");
                            }
                            return RemoteAddon.Dependency.ofGeneral(RELATION_TYPE.get(dependency.relationType()), RemoteAddon.Source.CURSEFORGE, Integer.toString(dependency.modId()));
                        }).distinct().filter(Objects::nonNull).collect(Collectors.toList()),
                        gameVersions.stream().filter(GameVersionNumber::isKnown).toList(),
                        gameVersions.stream()
                                .filter(AddonLoader::mightBeLoader)
                                .map(AddonLoader::of)
                                .toList()
                );
            }
        }

        /**
         * @see <a href="https://docs.curseforge.com/rest-api/#tocS_FileIndex">Schema</a>
         */
        @Immutable
        public record LatestFileIndex(String gameVersion, int fileId, String filename, int releaseType,
                                      int gameVersionTypeId, int modLoader) {
        }

    }

    @Immutable
    public static class Category {
        private final int id;
        private final int gameId;
        private final String name;
        private final String slug;
        private final String url;
        private final String iconUrl;
        private final Instant dateModified;
        private final boolean isClass;
        private final int classId;
        private final int parentCategoryId;

        private transient final List<Category> subcategories;

        public Category() {
            this(0, 0, "", "", "", "", Instant.now(), false, 0, 0);
        }

        public Category(int id, int gameId, String name, String slug, String url, String iconUrl, Instant dateModified, boolean isClass, int classId, int parentCategoryId) {
            this.id = id;
            this.gameId = gameId;
            this.name = name;
            this.slug = slug;
            this.url = url;
            this.iconUrl = iconUrl;
            this.dateModified = dateModified;
            this.isClass = isClass;
            this.classId = classId;
            this.parentCategoryId = parentCategoryId;

            this.subcategories = new ArrayList<>();
        }

        public int getId() {
            return id;
        }

        public int getGameId() {
            return gameId;
        }

        public String getName() {
            return name;
        }

        public String getSlug() {
            return slug;
        }

        public String getUrl() {
            return url;
        }

        public String getIconUrl() {
            return iconUrl;
        }

        public Instant getDateModified() {
            return dateModified;
        }

        public boolean isClass() {
            return isClass;
        }

        public int getClassId() {
            return classId;
        }

        public int getParentCategoryId() {
            return parentCategoryId;
        }

        public List<Category> getSubcategories() {
            return subcategories;
        }

        public RemoteAddonRepository.Category toCategory() {
            return new RemoteAddonRepository.Category(
                    this,
                    Integer.toString(id),
                    getSubcategories().stream().map(Category::toCategory).collect(Collectors.toList()));
        }
    }
}
