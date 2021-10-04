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
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.util.MurmurHash;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

public final class CurseForgeRemoteModRepository implements RemoteModRepository {

    private static final String PREFIX = "https://addons-ecs.forgesvc.net/api/v2";
    
    private final int section;

    public CurseForgeRemoteModRepository(int section) {
        this.section = section;
    }

    public List<CurseAddon> searchPaginated(String gameVersion, int category, int pageOffset, int pageSize, String searchFilter, int sort) throws IOException {
        String response = NetworkUtils.doGet(new URL(NetworkUtils.withQuery(PREFIX + "/addon/search", mapOf(
                pair("categoryId", Integer.toString(category)),
                pair("gameId", "432"),
                pair("gameVersion", gameVersion),
                pair("index", Integer.toString(pageOffset)),
                pair("pageSize", Integer.toString(pageSize)),
                pair("searchFilter", searchFilter),
                pair("sectionId", Integer.toString(section)),
                pair("sort", Integer.toString(sort))
        ))));
        return JsonUtils.fromNonNullJson(response, new TypeToken<List<CurseAddon>>() {
        }.getType());
    }

    @Override
    public Stream<RemoteModRepository.Mod> search(String gameVersion, RemoteModRepository.Category category, int pageOffset, int pageSize, String searchFilter, int sort) throws IOException {
        int categoryId = 0;
        if (category != null) categoryId = ((Category) category.getSelf()).getId();
        return searchPaginated(gameVersion, categoryId, pageOffset, pageSize, searchFilter, sort).stream()
                .map(CurseAddon::toMod);
    }

    @Override
    public Optional<RemoteModRepository.Version> getRemoteVersionByLocalFile(Path file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file)))) {
            int b;
            while ((b = reader.read()) != -1) {
                if (b != 0x9 && b != 0xa && b != 0xd && b != 0x20) {
                    baos.write(b);
                }
            }
        }

        int hash = MurmurHash.hash32(baos.toByteArray(), baos.size(), 1);

        FingerprintResponse response = HttpRequest.POST(PREFIX + "/fingerprint")
                .json(Collections.singletonList(hash))
                .getJson(FingerprintResponse.class);

        if (response.getExactMatches() == null || response.getExactMatches().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(response.getExactMatches().get(0).getFile().toVersion());
    }

    public CurseAddon getAddon(int id) throws IOException {
        String response = NetworkUtils.doGet(NetworkUtils.toURL(PREFIX + "/addon/" + id));
        return JsonUtils.fromNonNullJson(response, CurseAddon.class);
    }

    public List<CurseAddon.LatestFile> getFiles(CurseAddon addon) throws IOException {
        String response = NetworkUtils.doGet(NetworkUtils.toURL(PREFIX + "/addon/" + addon.getId() + "/files"));
        return JsonUtils.fromNonNullJson(response, new TypeToken<List<CurseAddon.LatestFile>>() {
        }.getType());
    }

    public List<Category> getCategoriesImpl() throws IOException {
        String response = NetworkUtils.doGet(NetworkUtils.toURL(PREFIX + "/category/section/" + section));
        List<Category> categories = JsonUtils.fromNonNullJson(response, new TypeToken<List<Category>>() {
        }.getType());
        return reorganizeCategories(categories, section);
    }

    @Override
    public Stream<RemoteModRepository.Category> getCategories() throws IOException {
        return getCategoriesImpl().stream().map(Category::toCategory);
    }

    private List<Category> reorganizeCategories(List<Category> categories, int rootId) {
        List<Category> result = new ArrayList<>();

        Map<Integer, Category> categoryMap = new HashMap<>();
        for (Category category : categories) {
            categoryMap.put(category.id, category);
        }
        for (Category category : categories) {
            if (category.parentGameCategoryId == rootId) {
                result.add(category);
            } else {
                Category parentCategory = categoryMap.get(category.parentGameCategoryId);
                if (parentCategory == null) {
                    // Category list is not correct, so we ignore this item.
                    continue;
                }
                parentCategory.subcategories.add(category);
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

    public static final CurseForgeRemoteModRepository MODS = new CurseForgeRemoteModRepository(SECTION_MOD);
    public static final CurseForgeRemoteModRepository MODPACKS = new CurseForgeRemoteModRepository(SECTION_MODPACK);
    public static final CurseForgeRemoteModRepository RESOURCE_PACKS = new CurseForgeRemoteModRepository(SECTION_RESOURCE_PACK);
    public static final CurseForgeRemoteModRepository WORLDS = new CurseForgeRemoteModRepository(SECTION_WORLD);

    public static class Category {
        private final int id;
        private final String name;
        private final String slug;
        private final String avatarUrl;
        private final int parentGameCategoryId;
        private final int rootGameCategoryId;
        private final int gameId;

        private final List<Category> subcategories;

        public Category() {
            this(0, "", "", "", 0, 0, 0, new ArrayList<>());
        }

        public Category(int id, String name, String slug, String avatarUrl, int parentGameCategoryId, int rootGameCategoryId, int gameId, List<Category> subcategories) {
            this.id = id;
            this.name = name;
            this.slug = slug;
            this.avatarUrl = avatarUrl;
            this.parentGameCategoryId = parentGameCategoryId;
            this.rootGameCategoryId = rootGameCategoryId;
            this.gameId = gameId;
            this.subcategories = subcategories;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getSlug() {
            return slug;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public int getParentGameCategoryId() {
            return parentGameCategoryId;
        }

        public int getRootGameCategoryId() {
            return rootGameCategoryId;
        }

        public int getGameId() {
            return gameId;
        }

        public List<Category> getSubcategories() {
            return subcategories;
        }

        public RemoteModRepository.Category toCategory() {
            return new RemoteModRepository.Category(
                    this,
                    Integer.toString(id),
                    getSubcategories().stream().map(Category::toCategory).collect(Collectors.toList()));
        }
    }

    private static class FingerprintResponse {
        private final boolean isCacheBuilt;
        private final List<CurseAddon> exactMatches;
        private final List<Integer> exactFingerprints;

        public FingerprintResponse(boolean isCacheBuilt, List<CurseAddon> exactMatches, List<Integer> exactFingerprints) {
            this.isCacheBuilt = isCacheBuilt;
            this.exactMatches = exactMatches;
            this.exactFingerprints = exactFingerprints;
        }

        public boolean isCacheBuilt() {
            return isCacheBuilt;
        }

        public List<CurseAddon> getExactMatches() {
            return exactMatches;
        }

        public List<Integer> getExactFingerprints() {
            return exactFingerprints;
        }
    }
}
