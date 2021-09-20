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
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.net.URL;
import java.util.*;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

public final class CurseModManager {
    private CurseModManager() {
    }

    public static List<CurseAddon> searchPaginated(String gameVersion, int category, int section, int pageOffset, String searchFilter, int sort) throws IOException {
        String response = NetworkUtils.doGet(new URL(NetworkUtils.withQuery("https://addons-ecs.forgesvc.net/api/v2/addon/search", mapOf(
                pair("categoryId", Integer.toString(category)),
                pair("gameId", "432"),
                pair("gameVersion", gameVersion),
                pair("index", Integer.toString(pageOffset)),
                pair("pageSize", "50"),
                pair("searchFilter", searchFilter),
                pair("sectionId", Integer.toString(section)),
                pair("sort", Integer.toString(sort))
        ))));
        return JsonUtils.fromNonNullJson(response, new TypeToken<List<CurseAddon>>() {
        }.getType());
    }

    public static CurseAddon getAddon(int id) throws IOException {
        String response = NetworkUtils.doGet(NetworkUtils.toURL("https://addons-ecs.forgesvc.net/api/v2/addon/" + id));
        return JsonUtils.fromNonNullJson(response, CurseAddon.class);
    }

    public static List<CurseAddon.LatestFile> getFiles(CurseAddon addon) throws IOException {
        String response = NetworkUtils.doGet(NetworkUtils.toURL("https://addons-ecs.forgesvc.net/api/v2/addon/" + addon.getId() + "/files"));
        return JsonUtils.fromNonNullJson(response, new TypeToken<List<CurseAddon.LatestFile>>() {
        }.getType());
    }

    public static List<Category> getCategories(int section) throws IOException {
        String response = NetworkUtils.doGet(NetworkUtils.toURL("https://addons-ecs.forgesvc.net/api/v2/category/section/" + section));
        List<Category> categories = JsonUtils.fromNonNullJson(response, new TypeToken<List<Category>>() {
        }.getType());
        return reorganizeCategories(categories, section);
    }

    private static List<Category> reorganizeCategories(List<Category> categories, int rootId) {
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
    }
}
