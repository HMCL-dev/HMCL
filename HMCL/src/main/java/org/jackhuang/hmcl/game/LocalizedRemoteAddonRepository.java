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

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.ui.versions.ModTranslations;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public abstract class LocalizedRemoteAddonRepository implements RemoteAddonRepository {
    private static final int CONTAIN_CHINESE_WEIGHT = 10;

    private static final int INITIAL_CAPACITY = 16;

    protected abstract RemoteAddonRepository getBackedRemoteModRepository();

    protected abstract SortType getBackedRemoteModRepositorySortOrder();

    @Override
    public SearchResult search(DownloadProvider downloadProvider, String gameVersion, Category category, int pageOffset, int pageSize, String searchFilter, SortType sort, SortOrder sortOrder) throws IOException {
        if (!StringUtils.containsChinese(searchFilter)) {
            return getBackedRemoteModRepository().search(downloadProvider, gameVersion, category, pageOffset, pageSize, searchFilter, sort, sortOrder);
        }

        Set<String> englishSearchFiltersSet = new LinkedHashSet<>(INITIAL_CAPACITY);

        int count = 0;
        for (ModTranslations.Mod mod : ModTranslations.getTranslationsByRepositoryType(getType()).searchMod(searchFilter)) {
            String englishSearchFilter = String.join(" ", StringUtils.tokenize(StringUtils.isNotBlank(mod.getSubname()) ? mod.getSubname() : mod.getName()));
            if (StringUtils.isNotBlank(englishSearchFilter)) {
                englishSearchFiltersSet.add(englishSearchFilter);
            }

            count++;
            if (count >= 3) break;
        }

        if (englishSearchFiltersSet.isEmpty()) {
            return getBackedRemoteModRepository().search(downloadProvider, gameVersion, category, pageOffset, pageSize, searchFilter, sort, sortOrder);
        }

        RemoteAddon[] searchResultArray = new RemoteAddon[pageSize];
        int totalPages, chineseIndex = 0, englishIndex = pageSize - 1;
        {
            SearchResult searchResult = null;
            List<RemoteAddon> remoteAddons = List.of();
            for (String englishSearchFilter : englishSearchFiltersSet) {
                searchResult = getBackedRemoteModRepository().search(downloadProvider, gameVersion, category, pageOffset, pageSize, englishSearchFilter, getBackedRemoteModRepositorySortOrder(), sortOrder);
                remoteAddons = searchResult.getUnsortedResults().toList();
                if (!remoteAddons.isEmpty()) {
                    break;
                }
            }

            for (RemoteAddon remoteAddon : remoteAddons) {
                if (chineseIndex > englishIndex) {
                    LOG.warning("Too many search results! Are the backed remote mod repository broken? Or are the API broken?");
                    continue;
                }

                ModTranslations.Mod chineseTranslation = ModTranslations.getTranslationsByRepositoryType(getType()).getModByCurseForgeId(remoteAddon.getSlug());
                if (chineseTranslation != null && !StringUtils.isBlank(chineseTranslation.getName()) && StringUtils.containsChinese(chineseTranslation.getName())) {
                    searchResultArray[chineseIndex++] = remoteAddon;
                } else {
                    searchResultArray[englishIndex--] = remoteAddon;
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
    public Optional<RemoteAddon.Version> getRemoteVersionByLocalFile(Path file) throws IOException {
        return getBackedRemoteModRepository().getRemoteVersionByLocalFile(file);
    }

    @Override
    public RemoteAddon getModById(DownloadProvider downloadProvider, String id) throws IOException {
        return getBackedRemoteModRepository().getModById(downloadProvider, id);
    }

    @Override
    public RemoteAddon.File getModFile(String modId, String fileId) throws IOException {
        return getBackedRemoteModRepository().getModFile(modId, fileId);
    }

    @Override
    public Stream<RemoteAddon.Version> getRemoteVersionsById(DownloadProvider downloadProvider, String id) throws IOException {
        return getBackedRemoteModRepository().getRemoteVersionsById(downloadProvider, id);
    }
}
