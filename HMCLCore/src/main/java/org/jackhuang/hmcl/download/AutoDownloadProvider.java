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

import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.task.Task;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author huangyuhui
public final class AutoDownloadProvider implements DownloadProvider {
    private final List<DownloadProvider> versionListProviders;
    private final List<DownloadProvider> fileProviders;
    private final ConcurrentMap<GameComponentType, ComponentVersionList<?>> versionLists = new ConcurrentHashMap<>();

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
    public ComponentVersionList<?> getVersionList(GameComponentType componentType) {
        return versionLists.computeIfAbsent(componentType, value -> {
            ComponentVersionList<?>[] lists = new ComponentVersionList<?>[versionListProviders.size()];
            for (int i = 0; i < versionListProviders.size(); i++) {
                lists[i] = versionListProviders.get(i).getVersionList(value);
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

    private static final class MultipleSourceVersionList extends ComponentVersionList<ComponentRemoteVersion> {
        private final ComponentVersionList<?>[] backends;

        MultipleSourceVersionList(ComponentVersionList<?>[] backends) {
            this.backends = backends;

            assert (backends.length >= 1);
        }

        @Override
        public boolean hasType() {
            boolean hasType = backends[0].hasType();
            assert (Arrays.stream(backends).allMatch(versionList -> versionList.hasType() == hasType));
            return hasType;
        }

        @Override
        public Task<?> refreshAsync() {
            throw new UnsupportedOperationException("MultipleSourceVersionList does not support loading the entire remote version list.");
        }

        private Task<?> refreshAsync(String gameVersion, int sourceIndex) {
            ComponentVersionList<?> versionList = backends[sourceIndex];
            Task<?> refreshTask = versionList.refreshAsync(gameVersion);

            return new Task<>() {
                private Task<?> nextTask = null;

                {
                    setSignificance(TaskSignificance.MODERATE);
                    setName("MultipleSourceVersionList.refreshAsync(task=%s, index=%d, all=%d)".formatted(
                            refreshTask.getName(), sourceIndex, backends.length)
                    );
                }

                @Override
                public Collection<Task<?>> getDependents() {
                    return List.of(refreshTask);
                }

                @Override
                public Collection<? extends Task<?>> getDependencies() {
                    return nextTask != null ? List.of(nextTask) : List.of();
                }

                @Override
                public boolean isRelyingOnDependents() {
                    return false;
                }

                @Override
                public void execute() throws Exception {
                    if (isDependentsSucceeded()) {
                        lock.writeLock().lock();
                        try {
                            versions.putAll(gameVersion, versionList.getVersions(gameVersion));
                        } finally {
                            lock.writeLock().unlock();
                        }

                        setResult(refreshTask.getResult());
                    } else {
                        Exception exception = refreshTask.getException();
                        assert exception != null;

                        if (sourceIndex == backends.length - 1) {
                            LOG.warning("Failed to fetch versions list from all sources", exception);
                            setSignificance(TaskSignificance.MINOR);
                            throw exception;
                        } else {
                            LOG.warning("Failed to fetch versions list and try to fetch from other source", exception);
                            nextTask = refreshAsync(gameVersion, sourceIndex + 1);
                            nextTask.storeTo(this::setResult);
                        }
                    }
                }
            };
        }

        @Override
        public Task<?> refreshAsync(String gameVersion) {
            versions.clear(gameVersion);
            return refreshAsync(gameVersion, 0);
        }
    }
}
