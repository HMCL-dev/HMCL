// SPDX-FileCopyrightText: Copyright (C) 2022 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.AddonProjectInfo;
import org.jackhuang.hmcl.addon.AddonProjectInfoRegistry;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public abstract class LocalizedRemoteAddonRepository implements RemoteAddonRepository {
    private static final int CONTAIN_CHINESE_WEIGHT = 10;

    private static final int INITIAL_CAPACITY = 16;

    protected abstract RemoteAddonRepository getBackedRemoteModRepository();

    @Override
    public String getApiBaseUrl() {
        return getBackedRemoteModRepository().getApiBaseUrl();
    }

    @Override
    public String getBaseUrl() {
        return getBackedRemoteModRepository().getBaseUrl();
    }

    @Override
    public SearchResult search(DownloadProvider downloadProvider, RemoteAddon.Type type, String gameVersion, Category category, int pageOffset, int pageSize, String searchFilter, SortType sort, SortOrder sortOrder) throws IOException {
        @Nullable AddonProjectInfoRegistry<?> registry = AddonProjectInfoRegistry.getByAddonType(type);
        if (registry == null || !StringUtils.containsChinese(searchFilter)) {
            return getBackedRemoteModRepository().search(downloadProvider, type, gameVersion, category, pageOffset, pageSize, searchFilter, sort, sortOrder);
        }

        Set<String> englishSearchFiltersSet = new LinkedHashSet<>(INITIAL_CAPACITY);

        int count = 0;
        for (AddonProjectInfo mod : registry.search(searchFilter)) {
            String englishSearchFilter = String.join(" ", StringUtils.tokenize(StringUtils.isNotBlank(mod.subName()) ? mod.subName() : mod.mainName()));
            if (StringUtils.isNotBlank(englishSearchFilter)) {
                englishSearchFiltersSet.add(englishSearchFilter);
            }

            count++;
            if (count >= 3) break;
        }

        if (englishSearchFiltersSet.isEmpty()) {
            return getBackedRemoteModRepository().search(downloadProvider, type, gameVersion, category, pageOffset, pageSize, searchFilter, sort, sortOrder);
        }

        RemoteAddon[] searchResultArray = new RemoteAddon[pageSize];
        int totalPages, chineseIndex = 0, englishIndex = pageSize - 1;
        {
            SearchResult searchResult = null;
            List<RemoteAddon> remoteAddons = List.of();
            for (String englishSearchFilter : englishSearchFiltersSet) {
                searchResult = getBackedRemoteModRepository().search(downloadProvider, type, gameVersion, category, pageOffset, pageSize, englishSearchFilter, SortType.RELEVANCY, sortOrder);
                remoteAddons = searchResult.results().toList();
                if (!remoteAddons.isEmpty()) {
                    break;
                }
            }

            for (RemoteAddon remoteAddon : remoteAddons) {
                if (chineseIndex > englishIndex) {
                    LOG.warning("Too many search results! Are the backed remote mod repository broken? Or are the API broken?");
                    continue;
                }

                @Nullable AddonProjectInfo chineseTranslation = registry.getInfoByCurseForgeId(remoteAddon.slug());
                if (chineseTranslation != null && !StringUtils.isBlank(chineseTranslation.mainName()) && StringUtils.containsChinese(chineseTranslation.mainName())) {
                    searchResultArray[chineseIndex++] = remoteAddon;
                } else {
                    searchResultArray[englishIndex--] = remoteAddon;
                }
            }
            totalPages = searchResult.totalPages();
        }

        StringUtils.LevCalculator levCalculator = new StringUtils.LevCalculator();
        return new SearchResult(Stream.concat(Arrays.stream(searchResultArray, 0, chineseIndex).map(remoteMod -> {
            @Nullable AddonProjectInfo chineseRemoteMod = registry.getInfoByCurseForgeId(remoteMod.slug());
            if (chineseRemoteMod == null || StringUtils.isBlank(chineseRemoteMod.mainName()) || !StringUtils.containsChinese(chineseRemoteMod.mainName())) {
                return Pair.pair(remoteMod, Integer.MAX_VALUE);
            }
            String chineseRemoteModName = chineseRemoteMod.mainName();
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
    public Stream<Category> getCategories(RemoteAddon.Type type) throws IOException {
        return getBackedRemoteModRepository().getCategories(type);
    }

    @Override
    public Optional<RemoteAddon.Version> getRemoteVersionByLocalFile(Path file) throws IOException {
        return getBackedRemoteModRepository().getRemoteVersionByLocalFile(file);
    }

    @Override
    public RemoteAddon getAddonById(DownloadProvider downloadProvider, String id) throws IOException {
        return getBackedRemoteModRepository().getAddonById(downloadProvider, id);
    }

    @Override
    public RemoteAddon resolveDependency(DownloadProvider downloadProvider, String id) throws IOException {
        return getBackedRemoteModRepository().resolveDependency(downloadProvider, id);
    }

    @Override
    public RemoteAddon.File getAddonFile(String projectId, String fileId) throws IOException {
        return getBackedRemoteModRepository().getAddonFile(projectId, fileId);
    }

    @Override
    public Stream<RemoteAddon.Version> getRemoteVersionsById(DownloadProvider downloadProvider, String id) throws IOException {
        return getBackedRemoteModRepository().getRemoteVersionsById(downloadProvider, id);
    }

    @Override
    public String getAddonChangelog(DownloadProvider downloadProvider, String addonId, String versionId) throws IOException {
        return getBackedRemoteModRepository().getAddonChangelog(downloadProvider, addonId, versionId);
    }

    @Override
    public @NotNull String getVersionPageUrl(RemoteAddon.Version version) throws IOException {
        return getBackedRemoteModRepository().getVersionPageUrl(version);
    }
}
