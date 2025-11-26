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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/// @author huangyuhui
public final class AutoDownloadProvider implements DownloadProvider {
    private final List<DownloadProvider> versionListProviders;
    private final List<DownloadProvider> fileProviders;
    private final ConcurrentMap<String, VersionList<?>> versionLists = new ConcurrentHashMap<>();

    public AutoDownloadProvider(
            List<DownloadProvider> versionListProviders,
            List<DownloadProvider> fileProviders) {
        if (versionListProviders == null || versionListProviders.isEmpty()) {
            throw new IllegalArgumentException("versionListProviders must not be null or empty");
        }

        if (fileProviders == null || fileProviders.isEmpty()) {
            throw new IllegalArgumentException("fileProviders must not be null or empty");
        }

        this.versionListProviders = versionListProviders;
        this.fileProviders = fileProviders;
    }

    public AutoDownloadProvider(DownloadProvider... downloadProviderCandidate) {
        if (downloadProviderCandidate.length == 0) {
            throw new IllegalArgumentException("Download provider must have at least one download provider");
        }

        this.versionListProviders = List.of(downloadProviderCandidate);
        this.fileProviders = versionListProviders;
    }

    private DownloadProvider getPreferredDownloadProvider() {
        return fileProviders.get(0);
    }

    private static List<URI> getAll(
            List<DownloadProvider> providers,
            Function<DownloadProvider, List<URI>> function) {
        LinkedHashSet<URI> result = new LinkedHashSet<>();
        for (DownloadProvider provider : providers) {
            result.addAll(function.apply(provider));
        }
        return List.copyOf(result);
    }

    @Override
    public List<URI> getVersionListURLs() {
        return getAll(versionListProviders, DownloadProvider::getVersionListURLs);
    }

    @Override
    public String injectURL(String baseURL) {
        return getPreferredDownloadProvider().injectURL(baseURL);
    }

    @Override
    public List<URI> getAssetObjectCandidates(String assetObjectLocation) {
        return getAll(fileProviders, provider -> provider.getAssetObjectCandidates(assetObjectLocation));
    }

    @Override
    public List<URI> injectURLWithCandidates(String baseURL) {
        return getAll(fileProviders, provider -> provider.injectURLWithCandidates(baseURL));
    }

    @Override
    public List<URI> injectURLsWithCandidates(List<String> urls) {
        return getAll(fileProviders, provider -> provider.injectURLsWithCandidates(urls));
    }

    @Override
    public VersionList<?> getVersionListById(String id) {
        return versionLists.computeIfAbsent(id, value -> {
            VersionList<?>[] lists = new VersionList<?>[versionListProviders.size()];
            for (int i = 0; i < versionListProviders.size(); i++) {
                lists[i] = versionListProviders.get(i).getVersionListById(value);
            }
            return new MultipleSourceVersionList(lists);
        });
    }

    @Override
    public int getConcurrency() {
        return getPreferredDownloadProvider().getConcurrency();
    }

    @Override
    public String toString() {
        return "AutoDownloadProvider[versionListProviders=%s, fileProviders=%s]".formatted(versionListProviders, fileProviders);
    }
}
