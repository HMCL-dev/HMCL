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
package org.jackhuang.hmcl.ui.download;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;

import java.net.URL;
import java.util.List;

public class InstallerWizardDownloadProvider implements DownloadProvider {

    private DownloadProvider fallback;

    public InstallerWizardDownloadProvider(DownloadProvider fallback) {
        this.fallback = fallback;
    }

    public void setDownloadProvider(DownloadProvider downloadProvider) {
        fallback = downloadProvider;
    }

    @Override
    public String getVersionListURL() {
        return fallback.getVersionListURL();
    }

    @Override
    public String getAssetBaseURL() {
        return fallback.getAssetBaseURL();
    }

    @Override
    public List<URL> getAssetObjectCandidates(String assetObjectLocation) {
        return fallback.getAssetObjectCandidates(assetObjectLocation);
    }

    @Override
    public String injectURL(String baseURL) {
        return fallback.injectURL(baseURL);
    }

    @Override
    public List<URL> injectURLWithCandidates(String baseURL) {
        return fallback.injectURLWithCandidates(baseURL);
    }

    @Override
    public VersionList<?> getVersionListById(String id) {
        return fallback.getVersionListById(id);
    }

    @Override
    public int getConcurrency() {
        return fallback.getConcurrency();
    }
}
