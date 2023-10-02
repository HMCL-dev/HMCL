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
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public abstract class LocalizedRemoteModRepository implements RemoteModRepository {
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
        Set<Character> searchFilterLetters = new HashSet<>();
        for (int i = 0; i < searchFilter.length(); i++) {
            searchFilterLetters.add(searchFilter.charAt(i));
        }

        RemoteMod[] searchResultArray = new RemoteMod[pageSize];
        int chineseIndex = 0, englishIndex = searchResultArray.length - 1;
        for (Iterator<RemoteMod> iterator = searchResult.getUnsortedResults().iterator(); iterator.hasNext(); ) {
            if (chineseIndex > englishIndex) {
                throw new IOException("There are too many search results!");
            }

            RemoteMod remoteMod = iterator.next();
            ModTranslations.Mod chineseTranslation = ModTranslations.getTranslationsByRepositoryType(getType()).getModByCurseForgeId(remoteMod.getSlug());
            if (chineseTranslation != null && !StringUtils.isBlank(chineseTranslation.getName()) && StringUtils.containsChinese(chineseTranslation.getName())) {
                searchResultArray[chineseIndex++] = remoteMod;
            } else {
                searchResultArray[englishIndex--] = remoteMod;
            }
        }
        int totalPages = searchResult.getTotalPages();
        searchResult = null; // Release memory

        return new SearchResult(Stream.concat(Arrays.stream(searchResultArray, 0, chineseIndex).map(remoteMod -> {
            ModTranslations.Mod chineseRemoteMod = ModTranslations.getTranslationsByRepositoryType(getType()).getModByCurseForgeId(remoteMod.getSlug());
            if (chineseRemoteMod == null || StringUtils.isBlank(chineseRemoteMod.getName()) || !StringUtils.containsChinese(chineseRemoteMod.getName())) {
                return Pair.pair(remoteMod, Integer.MAX_VALUE);
            }
            String chineseRemoteModName = chineseRemoteMod.getName();
            if (searchFilter.length() == 0 || chineseRemoteModName.length() == 0) {
                return Pair.pair(remoteMod, Math.max(searchFilter.length(), chineseRemoteModName.length()));
            }
            int[][] lev = new int[searchFilter.length() + 1][chineseRemoteModName.length() + 1];
            for (int i = 0; i < chineseRemoteModName.length() + 1; i++) {
                lev[0][i] = i;
            }
            for (int i = 0; i < searchFilter.length() + 1; i++) {
                lev[i][0] = i;
            }
            for (int i = 1; i < searchFilter.length() + 1; i++) {
                for (int j = 1; j < chineseRemoteModName.length() + 1; j++) {
                    int countByInsert = lev[i][j - 1] + 1;
                    int countByDel = lev[i - 1][j] + 1;
                    int countByReplace = searchFilter.charAt(i - 1) == chineseRemoteModName.charAt(j - 1) ? lev[i - 1][j - 1] : lev[i - 1][j - 1] + 1;
                    lev[i][j] = Math.min(countByInsert, Math.min(countByDel, countByReplace));
                }
            }

            return Pair.pair(
                    remoteMod,
                    lev[searchFilter.length()][chineseRemoteModName.length()] - (searchFilterLetters.stream().anyMatch(c -> chineseRemoteModName.indexOf(c) >= 0) ? CONTAIN_CHINESE_WEIGHT : 0)
            );
        }).sorted(Comparator.comparingInt(Pair::getValue)).map(Pair::getKey), Arrays.stream(searchResultArray, englishIndex + 1, searchResultArray.length)), totalPages);
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
