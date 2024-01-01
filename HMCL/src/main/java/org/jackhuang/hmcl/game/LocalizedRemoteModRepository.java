/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.ui.versions.ModTranslations;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public abstract class LocalizedRemoteModRepository implements RemoteModRepository {
    // Yes, I'm not kidding you. The similarity check is based on these two magic number. :)
    private static final int CONTAIN_CHINESE_WEIGHT = 10;

    private static final int INITIAL_CAPACITY = 16;

    protected abstract RemoteModRepository getBackedRemoteModRepository();

    protected abstract SortType getBackedRemoteModRepositorySortOrder();

    @Override
    public SearchResult search(String gameVersion, Category category, int pageOffset, int pageSize, String searchFilter, SortType sort, SortOrder sortOrder) throws IOException {
        if (!StringUtils.containsChinese(searchFilter)) {
            return getBackedRemoteModRepository().search(gameVersion, category, pageOffset, pageSize, searchFilter, sort, sortOrder);
        }

        Set<String> englishSearchFiltersSet = new HashSet<>(INITIAL_CAPACITY);

        int count = 0;
        for (ModTranslations.Mod mod : ModTranslations.getTranslationsByRepositoryType(getType()).searchMod(searchFilter)) {
            for (String englishWord : StringUtils.tokenize(StringUtils.isNotBlank(mod.getSubname()) ? mod.getSubname() : mod.getName())) {
                if (englishSearchFiltersSet.contains(englishWord)) {
                    continue;
                }

                englishSearchFiltersSet.add(englishWord);
            }

            count++;
            if (count >= 3) break;
        }

        SearchResult searchResult = getBackedRemoteModRepository().search(gameVersion, category, pageOffset, pageSize, String.join(" ", englishSearchFiltersSet), getBackedRemoteModRepositorySortOrder(), sortOrder);

        RemoteMod[] searchResultArray = new RemoteMod[pageSize];
        int chineseIndex = 0, englishIndex = searchResultArray.length - 1;
        for (RemoteMod remoteMod : Lang.toIterable(searchResult.getUnsortedResults())) {
            if (chineseIndex > englishIndex) {
                throw new IOException("There are too many search results!");
            }

            ModTranslations.Mod chineseTranslation = ModTranslations.getTranslationsByRepositoryType(getType()).getModByCurseForgeId(remoteMod.getSlug());
            if (chineseTranslation != null && !StringUtils.isBlank(chineseTranslation.getName()) && StringUtils.containsChinese(chineseTranslation.getName())) {
                searchResultArray[chineseIndex++] = remoteMod;
            } else {
                searchResultArray[englishIndex--] = remoteMod;
            }
        }
        int totalPages = searchResult.getTotalPages();
        searchResult = null; // Release memory

        StringUtils.DynamicCommonSubsequence calc = new StringUtils.DynamicCommonSubsequence(16, 16);
        return new SearchResult(Stream.concat(Arrays.stream(searchResultArray, 0, chineseIndex).map(remoteMod -> {
            ModTranslations.Mod chineseRemoteMod = ModTranslations.getTranslationsByRepositoryType(getType()).getModByCurseForgeId(remoteMod.getSlug());
            if (chineseRemoteMod == null || StringUtils.isBlank(chineseRemoteMod.getName()) || !StringUtils.containsChinese(chineseRemoteMod.getName())) {
                return Pair.pair(remoteMod, Integer.MAX_VALUE);
            }

            String chineseRemoteModName = chineseRemoteMod.getName();
            if (searchFilter.isEmpty() || chineseRemoteModName.isEmpty()) {
                return Pair.pair(remoteMod, Math.max(searchFilter.length(), chineseRemoteModName.length()));
            }

            int weight = calc.calc(searchFilter, chineseRemoteModName);
            for (int i = 0;i < searchFilter.length(); i ++) {
                if (chineseRemoteModName.indexOf(searchFilter.charAt(i)) >= 0) {
                    return Pair.pair(remoteMod, weight + CONTAIN_CHINESE_WEIGHT);
                }
            }
            return Pair.pair(remoteMod, weight);
        }).sorted(Comparator.<Pair<RemoteMod, Integer>>comparingInt(Pair::getValue).reversed()).map(Pair::getKey), Arrays.stream(searchResultArray, englishIndex + 1, searchResultArray.length)), totalPages);
    }

    @Override
    public Stream<Category> getCategories() throws IOException {
        return getBackedRemoteModRepository().getCategories();
    }

    @Override
    public Optional<RemoteMod.Version> getRemoteVersionByLocalFile(LocalModFile localModFile, Path file) throws IOException {
        return getBackedRemoteModRepository().getRemoteVersionByLocalFile(localModFile, file);
    }

    @Override
    public RemoteMod getModById(String id) throws IOException {
        return getBackedRemoteModRepository().getModById(id);
    }

    @Override
    public RemoteMod.File getModFile(String modId, String fileId) throws IOException {
        return getBackedRemoteModRepository().getModFile(modId, fileId);
    }

    @Override
    public Stream<RemoteMod.Version> getRemoteVersionsById(String id) throws IOException {
        return getBackedRemoteModRepository().getRemoteVersionsById(id);
    }
}
