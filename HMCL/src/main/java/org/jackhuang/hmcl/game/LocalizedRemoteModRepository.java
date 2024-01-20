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
import java.util.logging.Level;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.Logging.LOG;

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

        RemoteMod[] searchResultArray = new RemoteMod[pageSize];
        int totalPages, chineseIndex = 0, englishIndex = pageSize - 1;
        {
            SearchResult searchResult = getBackedRemoteModRepository().search(gameVersion, category, pageOffset, pageSize, String.join(" ", englishSearchFiltersSet), getBackedRemoteModRepositorySortOrder(), sortOrder);
            for (Iterator<RemoteMod> iterator = searchResult.getUnsortedResults().iterator(); iterator.hasNext(); ) {
                if (chineseIndex > englishIndex) {
                    LOG.log(Level.WARNING, "Too many search results! Are the backed remote mod repository broken? Or are the API broken?");
                    continue;
                }

                RemoteMod remoteMod = iterator.next();
                ModTranslations.Mod chineseTranslation = ModTranslations.getTranslationsByRepositoryType(getType()).getModByCurseForgeId(remoteMod.getSlug());
                if (chineseTranslation != null && !StringUtils.isBlank(chineseTranslation.getName()) && StringUtils.containsChinese(chineseTranslation.getName())) {
                    searchResultArray[chineseIndex++] = remoteMod;
                } else {
                    searchResultArray[englishIndex--] = remoteMod;
                }
            }
            totalPages = searchResult.getTotalPages();
        }

        StringUtils.LevCalculator levCalculator = new StringUtils.LevCalculator();
        return new SearchResult(Stream.concat(Arrays.stream(searchResultArray, 0, chineseIndex).map(remoteMod -> {
            ModTranslations.Mod chineseRemoteMod = ModTranslations.getTranslationsByRepositoryType(getType()).getModByCurseForgeId(remoteMod.getSlug());
            if (chineseRemoteMod == null || StringUtils.isBlank(chineseRemoteMod.getName()) || !StringUtils.containsChinese(chineseRemoteMod.getName())) {
                return Pair.pair(remoteMod, Integer.MAX_VALUE);
            }
            String chineseRemoteModName = chineseRemoteMod.getName();
            if (searchFilter.isEmpty() || chineseRemoteModName.isEmpty()) {
                return Pair.pair(remoteMod, Math.max(searchFilter.length(), chineseRemoteModName.length()));
            }
            int l = levCalculator.calc(searchFilter, chineseRemoteModName);
            for (int i = 0; i < searchFilter.length(); i++) {
                if (chineseRemoteModName.indexOf(searchFilter.charAt(i)) >= 0) {
                    l -= CONTAIN_CHINESE_WEIGHT;
                }
            }
            return Pair.pair(remoteMod, l);
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
