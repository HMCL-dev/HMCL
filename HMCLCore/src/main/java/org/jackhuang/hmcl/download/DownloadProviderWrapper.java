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

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * @author Glavo
 */
public final class DownloadProviderWrapper implements DownloadProvider {

    private DownloadProvider provider;

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
    public List<URI> getAssetObjectCandidates(String assetObjectLocation) {
        return getProvider().getAssetObjectCandidates(assetObjectLocation);
    }

    @Override
    public String getVersionListURL() {
        return getProvider().getVersionListURL();
    }

    @Override
    public String getAssetBaseURL() {
        return getProvider().getAssetBaseURL();
    }

    @Override
    public String injectURL(String baseURL) {
        return getProvider().injectURL(baseURL);
    }

    @Override
    public List<URI> injectURLWithCandidates(String baseURL) {
        return getProvider().injectURLWithCandidates(baseURL);
    }

    @Override
    public List<URI> injectURLsWithCandidates(List<String> urls) {
        return getProvider().injectURLsWithCandidates(urls);
    }

    @Override
    public VersionList<?> getVersionListById(String id) {
        return getProvider().getVersionListById(id);
    }

    @Override
    public int getConcurrency() {
        return getProvider().getConcurrency();
    }
}
