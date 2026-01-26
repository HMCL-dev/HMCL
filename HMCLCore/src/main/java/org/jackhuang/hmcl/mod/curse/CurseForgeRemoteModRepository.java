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
package org.jackhuang.hmcl.mod.curse;

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.util.MurmurHash2;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;

public final class CurseForgeRemoteModRepository implements RemoteModRepository {

    private static final String PREFIX = "https://api.curseforge.com";
    private static final String apiKey = System.getProperty("hmcl.curseforge.apikey", JarUtils.getAttribute("hmcl.curseforge.apikey", ""));
    private static final Semaphore SEMAPHORE = new Semaphore(16);

    private static final int WORD_PERFECT_MATCH_WEIGHT = 5;

    private static <R extends HttpRequest> R withApiKey(R request) {
        if (request.getUrl().startsWith(PREFIX) && !apiKey.isEmpty()) {
            request.header("X-API-KEY", apiKey);
        }
        return request;
    }

    public static boolean isAvailable() {
        return !apiKey.isEmpty();
    }

    private final Type type;
    private final int section;

    public CurseForgeRemoteModRepository(Type type, int section) {
        this.type = type;
        this.section = section;
    }

    @Override
    public Type getType() {
        return type;
    }

    private int toModsSearchSortField(SortType sort) {
        // https://docs.curseforge.com/#tocS_ModsSearchSortField
        switch (sort) {
            case DATE_CREATED:
                return 1;
            case POPULARITY:
                return 2;
            case LAST_UPDATED:
                return 3;
            case NAME:
                return 4;
            case AUTHOR:
                return 5;
            case TOTAL_DOWNLOADS:
                return 6;
            default:
                return 8;
        }
    }

    private String toSortOrder(SortOrder sortOrder) {
        // https://docs.curseforge.com/#tocS_SortOrder
        switch (sortOrder) {
            case ASC:
                return "asc";
            case DESC:
                return "desc";
        }
        return "asc";
    }

    private int calculateTotalPages(Response<List<CurseAddon>> response, int pageSize) {
        return (int) Math.ceil((double) Math.min(response.pagination.totalCount, 10000) / pageSize);
    }

    @Override
    public SearchResult search(DownloadProvider downloadProvider, String gameVersion, @Nullable RemoteModRepository.Category category, int pageOffset, int pageSize, String searchFilter, SortType sortType, SortOrder sortOrder) throws IOException {
        SEMAPHORE.acquireUninterruptibly();
        try {
            int categoryId = 0;
            if (category != null && category.getSelf() instanceof CurseAddon.Category) {
                categoryId = ((CurseAddon.Category) category.getSelf()).getId();
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

            Response<List<CurseAddon>> response = withApiKey(HttpRequest.GET(downloadProvider.injectURL(NetworkUtils.withQuery(PREFIX + "/v1/mods/search", query))))
                    .getJson(Response.typeOf(listTypeOf(CurseAddon.class)));
            if (searchFilter.isEmpty()) {
                return new SearchResult(response.getData().stream().map(CurseAddon::toMod), calculateTotalPages(response, pageSize));
            }

            // https://github.com/HMCL-dev/HMCL/issues/1549
            String lowerCaseSearchFilter = searchFilter.toLowerCase(Locale.ROOT);
            Map<String, Integer> searchFilterWords = new HashMap<>();
            for (String s : StringUtils.tokenize(lowerCaseSearchFilter)) {
                searchFilterWords.put(s, searchFilterWords.getOrDefault(s, 0) + 1);
            }

            StringUtils.LevCalculator levCalculator = new StringUtils.LevCalculator();

            return new SearchResult(response.getData().stream().map(CurseAddon::toMod).map(remoteMod -> {
                String lowerCaseResult = remoteMod.getTitle().toLowerCase(Locale.ROOT);
                int diff = levCalculator.calc(lowerCaseSearchFilter, lowerCaseResult);

                for (String s : StringUtils.tokenize(lowerCaseResult)) {
                    if (searchFilterWords.containsKey(s)) {
                        diff -= WORD_PERFECT_MATCH_WEIGHT * searchFilterWords.get(s) * s.length();
                    }
                }

                return pair(remoteMod, diff);
            }).sorted(Comparator.comparingInt(Pair::getValue)).map(Pair::getKey), response.getData().stream().map(CurseAddon::toMod), calculateTotalPages(response, pageSize));
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public Optional<RemoteMod.Version> getRemoteVersionByLocalFile(LocalModFile localModFile, Path file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = stream.read(buf, 0, buf.length)) != -1) {
                for (int i = 0; i < len; i++) {
                    byte b = buf[i];
                    if (b != 0x9 && b != 0xa && b != 0xd && b != 0x20) {
                        baos.write(b);
                    }
                }
            }
        }

        long hash = Integer.toUnsignedLong(MurmurHash2.hash32(baos.toByteArray(), baos.size(), 1));
        if (hash == 811513880) { // Workaround for https://github.com/HMCL-dev/HMCL/issues/4597
            return Optional.empty();
        }

        SEMAPHORE.acquireUninterruptibly();
        try {
            Response<FingerprintMatchesResult> response = withApiKey(HttpRequest.POST(PREFIX + "/v1/fingerprints/432"))
                    .json(mapOf(pair("fingerprints", Collections.singletonList(hash))))
                    .getJson(Response.typeOf(FingerprintMatchesResult.class));

            if (response.getData().getExactMatches() == null || response.getData().getExactMatches().isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(response.getData().getExactMatches().get(0).getFile().toVersion());
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public RemoteMod getModById(String id) throws IOException {
        SEMAPHORE.acquireUninterruptibly();
        try {
            Response<CurseAddon> response = withApiKey(HttpRequest.GET(PREFIX + "/v1/mods/" + id))
                    .getJson(Response.typeOf(CurseAddon.class));
            return response.data.toMod();
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public RemoteMod.File getModFile(String modId, String fileId) throws IOException {
        SEMAPHORE.acquireUninterruptibly();
        try {
            Response<CurseAddon.LatestFile> response = withApiKey(HttpRequest.GET(String.format("%s/v1/mods/%s/files/%s", PREFIX, modId, fileId)))
                    .getJson(Response.typeOf(CurseAddon.LatestFile.class));
            return response.getData().toVersion().getFile();
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public Stream<RemoteMod.Version> getRemoteVersionsById(String id) throws IOException {
        SEMAPHORE.acquireUninterruptibly();
        try {
            Response<List<CurseAddon.LatestFile>> response = withApiKey(HttpRequest.GET(PREFIX + "/v1/mods/" + id + "/files",
                    pair("pageSize", "10000")))
                    .getJson(Response.typeOf(listTypeOf(CurseAddon.LatestFile.class)));
            return response.getData().stream().map(CurseAddon.LatestFile::toVersion);
        } finally {
            SEMAPHORE.release();
        }
    }

    @Override
    public Stream<RemoteModRepository.Category> getCategories() throws IOException {
        SEMAPHORE.acquireUninterruptibly();
        try {
            Response<List<CurseAddon.Category>> categories = withApiKey(HttpRequest.GET(PREFIX + "/v1/categories", pair("gameId", "432")))
                    .getJson(Response.typeOf(listTypeOf(CurseAddon.Category.class)));
            return reorganizeCategories(categories.getData(), section).stream().map(CurseAddon.Category::toCategory);
        } finally {
            SEMAPHORE.release();
        }
    }

    private List<CurseAddon.Category> reorganizeCategories(List<CurseAddon.Category> categories, int rootId) {
        List<CurseAddon.Category> result = new ArrayList<>();

        Map<Integer, CurseAddon.Category> categoryMap = new HashMap<>();
        for (CurseAddon.Category category : categories) {
            categoryMap.put(category.getId(), category);
        }
        for (CurseAddon.Category category : categories) {
            if (category.getParentCategoryId() == rootId) {
                result.add(category);
            } else {
                CurseAddon.Category parentCategory = categoryMap.get(category.getParentCategoryId());
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
    public static final int SECTION_WORLD = 17;
    public static final int SECTION_MODPACK = 4471;
    public static final int SECTION_CUSTOMIZATION = 4546;
    public static final int SECTION_ADDONS = 4559; // For Pocket Edition
    public static final int SECTION_UNKNOWN1 = 4944;
    public static final int SECTION_UNKNOWN2 = 4979;
    public static final int SECTION_UNKNOWN3 = 4984;

    public static final CurseForgeRemoteModRepository MODS = new CurseForgeRemoteModRepository(RemoteModRepository.Type.MOD, SECTION_MOD);
    public static final CurseForgeRemoteModRepository MODPACKS = new CurseForgeRemoteModRepository(RemoteModRepository.Type.MODPACK, SECTION_MODPACK);
    public static final CurseForgeRemoteModRepository RESOURCE_PACKS = new CurseForgeRemoteModRepository(RemoteModRepository.Type.RESOURCE_PACK, SECTION_RESOURCE_PACK);
    public static final CurseForgeRemoteModRepository WORLDS = new CurseForgeRemoteModRepository(RemoteModRepository.Type.WORLD, SECTION_WORLD);
    public static final CurseForgeRemoteModRepository CUSTOMIZATIONS = new CurseForgeRemoteModRepository(RemoteModRepository.Type.CUSTOMIZATION, SECTION_CUSTOMIZATION);

    public static class Pagination {
        private final int index;
        private final int pageSize;
        private final int resultCount;
        private final int totalCount;

        public Pagination(int index, int pageSize, int resultCount, int totalCount) {
            this.index = index;
            this.pageSize = pageSize;
            this.resultCount = resultCount;
            this.totalCount = totalCount;
        }

        public int getIndex() {
            return index;
        }

        public int getPageSize() {
            return pageSize;
        }

        public int getResultCount() {
            return resultCount;
        }

        public int getTotalCount() {
            return totalCount;
        }
    }

    public static class Response<T> {

        @SuppressWarnings("unchecked")
        public static <T> TypeToken<Response<T>> typeOf(Class<T> responseType) {
            return (TypeToken<Response<T>>) TypeToken.getParameterized(Response.class, responseType);
        }

        @SuppressWarnings("unchecked")
        public static <T> TypeToken<Response<T>> typeOf(TypeToken<T> responseType) {
            return (TypeToken<Response<T>>) TypeToken.getParameterized(Response.class, responseType.getType());
        }

        private final T data;
        private final Pagination pagination;

        public Response(T data, Pagination pagination) {
            this.data = data;
            this.pagination = pagination;
        }

        public T getData() {
            return data;
        }

        public Pagination getPagination() {
            return pagination;
        }
    }

    /**
     * @see <a href="https://docs.curseforge.com/#tocS_FingerprintsMatchesResult">Schema</a>
     */
    private static class FingerprintMatchesResult {
        private final boolean isCacheBuilt;
        private final List<FingerprintMatch> exactMatches;
        private final List<Long> exactFingerprints;

        public FingerprintMatchesResult(boolean isCacheBuilt, List<FingerprintMatch> exactMatches, List<Long> exactFingerprints) {
            this.isCacheBuilt = isCacheBuilt;
            this.exactMatches = exactMatches;
            this.exactFingerprints = exactFingerprints;
        }

        public boolean isCacheBuilt() {
            return isCacheBuilt;
        }

        public List<FingerprintMatch> getExactMatches() {
            return exactMatches;
        }

        public List<Long> getExactFingerprints() {
            return exactFingerprints;
        }
    }

    /**
     * @see <a href="https://docs.curseforge.com/#tocS_FingerprintMatch">Schema</a>
     */
    private static class FingerprintMatch {
        private final int id;
        private final CurseAddon.LatestFile file;
        private final List<CurseAddon.LatestFile> latestFiles;

        public FingerprintMatch(int id, CurseAddon.LatestFile file, List<CurseAddon.LatestFile> latestFiles) {
            this.id = id;
            this.file = file;
            this.latestFiles = latestFiles;
        }

        public int getId() {
            return id;
        }

        public CurseAddon.LatestFile getFile() {
            return file;
        }

        public List<CurseAddon.LatestFile> getLatestFiles() {
            return latestFiles;
        }
    }
}
