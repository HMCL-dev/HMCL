/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/**
 * @author Glavo
 */
public final class DownloadProviderWrapper implements DownloadProvider {

    private volatile DownloadProvider provider;

    public DownloadProviderWrapper(DownloadProvider provider) {
        this.provider = provider;
    }

    public DownloadProvider getProvider() {
        return this.provider;
    }

    public void setProvider(DownloadProvider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    @Override
    public @Unmodifiable List<WebURL> getAssetObjectCandidates(String assetObjectLocation) {
        return getProvider().getAssetObjectCandidates(assetObjectLocation);
    }

    @Override
    public @Unmodifiable List<WebURL> getVersionListURLs() {
        return getProvider().getVersionListURLs();
    }

    @Override
    public String injectURL(String baseURL) {
        return getProvider().injectURL(baseURL);
    }

    @Override
    public @Unmodifiable List<WebURL> injectURLWithCandidates(String baseURL) {
        return getProvider().injectURLWithCandidates(baseURL);
    }

    @Override
    public @Unmodifiable List<WebURL> injectURLsWithCandidates(List<String> urls) {
        return getProvider().injectURLsWithCandidates(urls);
    }

    @Override
    public ComponentVersionList<?> getVersionList(GameComponentType componentType) {
        return new ComponentVersionList<>() {
            @Override
            public boolean hasType() {
                return getProvider().getVersionList(componentType).hasType();
            }

            @Override
            public Task<?> refreshAsync() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Task<?> refreshAsync(String gameVersion) {
                return getProvider().getVersionList(componentType).refreshAsync(gameVersion)
                        .thenComposeAsync(() -> {
                            lock.writeLock().lock();
                            try {
                                versions.putAll(gameVersion, getProvider().getVersionList(componentType).getVersions(gameVersion));
                            } finally {
                                lock.writeLock().unlock();
                            }
                            return null;
                        });
            }
        };
    }

    @Override
    public int getConcurrency() {
        return getProvider().getConcurrency();
    }

    @Override
    public String toString() {
        return "DownloadProviderWrapper[provider=%s]".formatted(provider);
    }
}
