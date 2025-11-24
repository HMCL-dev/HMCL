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
package org.jackhuang.hmcl.download;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Official Download Provider fetches version list from Mojang and
 * download files from mcbbs.
 *
 * @author huangyuhui
 */
public final class AutoDownloadProvider implements DownloadProvider {
    private final List<DownloadProvider> versionListProviders;
    private final DownloadProvider fileProvider;

    private final Map<String, VersionList<?>> versionLists = new HashMap<>();

    public AutoDownloadProvider(
            List<DownloadProvider> versionListProviders,
            DownloadProvider fileProvider) {
        this.versionListProviders = versionListProviders;
        this.fileProvider = fileProvider;
    }

    @Override
    public List<URI> getVersionListURLs() {
        LinkedHashSet<URI> result = new LinkedHashSet<>();
        for (DownloadProvider provider : versionListProviders) {
            result.addAll(provider.getVersionListURLs());
        }
        return List.copyOf(result);
    }

    @Override
    public String injectURL(String baseURL) {
        return fileProvider.injectURL(baseURL);
    }

    @Override
    public List<URI> getAssetObjectCandidates(String assetObjectLocation) {
        return fileProvider.getAssetObjectCandidates(assetObjectLocation);
    }

    @Override
    public List<URI> injectURLWithCandidates(String baseURL) {
        return fileProvider.injectURLWithCandidates(baseURL);
    }

    @Override
    public List<URI> injectURLsWithCandidates(List<String> urls) {
        return fileProvider.injectURLsWithCandidates(urls);
    }

    @Override
    public VersionList<?> getVersionListById(String id) {
        if (versionListProviders.size() == 1) {
            return versionListProviders.get(0).getVersionListById(id);
        } else {
            return versionLists.computeIfAbsent(id, value -> {
                VersionList<?>[] lists = new VersionList<?>[versionListProviders.size()];
                for (int i = 0; i < versionListProviders.size(); i++) {
                    lists[i] = versionListProviders.get(i).getVersionListById(value);
                }
                return new MultipleSourceVersionList(lists);
            });
        }
    }

    @Override
    public int getConcurrency() {
        return fileProvider.getConcurrency();
    }
}
