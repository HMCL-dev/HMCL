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
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.util.MurmurHash2;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

public final class CurseForgeRemoteModRepository implements RemoteModRepository {

    private static final String PREFIX = "https://api.curseforge.com";

    private static String apiKey;

    static {
        apiKey = System.getProperty("hmcl.curseforge.apikey",
                JarUtils.thisJar().flatMap(JarUtils::getManifest).map(manifest -> manifest.getMainAttributes().getValue("CurseForge-Api-Key")).orElse(""));
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

    @Override
    public Stream<RemoteMod> search(String gameVersion, @Nullable RemoteModRepository.Category category, int pageOffset, int pageSize, String searchFilter, SortType sortType, SortOrder sortOrder) throws IOException {
        int categoryId = 0;
        if (category != null) categoryId = ((CurseAddon.Category) category.getSelf()).getId();
        Response<List<CurseAddon>> response = HttpRequest.GET(PREFIX + "/v1/mods/search",
                        pair("gameId", "432"),
                        pair("classId", Integer.toString(section)),
                        pair("categoryId", Integer.toString(categoryId)),
                        pair("gameVersion", gameVersion),
                        pair("searchFilter", searchFilter),
                        pair("sortField", Integer.toString(toModsSearchSortField(sortType))),
                        pair("sortOrder", toSortOrder(sortOrder)),
                        pair("index", Integer.toString(pageOffset)),
                        pair("pageSize", Integer.toString(pageSize)))
                .header("X-API-KEY", apiKey)
                .getJson(new TypeToken<Response<List<CurseAddon>>>() {
                }.getType());
        return response.getData().stream().map(CurseAddon::toMod);
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

        Response<FingerprintMatchesResult> response = HttpRequest.POST(PREFIX + "/v1/fingerprints")
                .json(mapOf(pair("fingerprints", Collections.singletonList(hash))))
                .header("X-API-KEY", apiKey)
                .getJson(new TypeToken<Response<FingerprintMatchesResult>>() {
                }.getType());

        if (response.getData().getExactMatches() == null || response.getData().getExactMatches().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(response.getData().getExactMatches().get(0).getFile().toVersion());
    }

    @Override
    public RemoteMod getModById(String id) throws IOException {
        return HttpRequest.GET(PREFIX + "/v1/mods/" + id)
                .header("X-API-KEY", apiKey)
                .getJson(CurseAddon.class).toMod();
    }

    @Override
    public RemoteMod.File getModFile(String modId, String fileId) throws IOException {
        Response<CurseAddon.LatestFile> response = HttpRequest.GET(String.format("%s/v1/mods/%s/files/%s", PREFIX, modId, fileId))
                .header("X-API-KEY", apiKey)
                .getJson(new TypeToken<Response<CurseAddon.LatestFile>>() {
                }.getType());
        return response.getData().toVersion().getFile();
    }

    @Override
    public Stream<RemoteMod.Version> getRemoteVersionsById(String id) throws IOException {
        Response<List<CurseAddon.LatestFile>> response = HttpRequest.GET(PREFIX + "/v1/mods/" + id + "/files",
                        pair("pageSize", "10000"))
                .header("X-API-KEY", apiKey)
                .getJson(new TypeToken<Response<List<CurseAddon.LatestFile>>>() {
                }.getType());
        return response.getData().stream().map(CurseAddon.LatestFile::toVersion);
    }

    public List<CurseAddon.Category> getCategoriesImpl() throws IOException {
        Response<List<CurseAddon.Category>> categories = HttpRequest.GET(PREFIX + "/v1/categories", pair("gameId", "432"))
                .header("X-API-KEY", apiKey)
                .getJson(new TypeToken<Response<List<CurseAddon.Category>>>() {
                }.getType());
        return reorganizeCategories(categories.getData(), section);
    }

    @Override
    public Stream<RemoteModRepository.Category> getCategories() throws IOException {
        return getCategoriesImpl().stream().map(CurseAddon.Category::toCategory);
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
