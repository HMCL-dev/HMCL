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

/**
 * Official Download Provider fetches version list from Mojang and
 * download files from mcbbs.
 *
 * @author huangyuhui
 */
public class AutoDownloadProvider implements DownloadProvider {
    private final DownloadProvider versionListProvider;
    private final DownloadProvider fileProvider;

    public AutoDownloadProvider(DownloadProvider versionListProvider, DownloadProvider fileProvider) {
        this.versionListProvider = versionListProvider;
        this.fileProvider = fileProvider;
    }

    @Override
    public String getVersionListURL() {
        return versionListProvider.getVersionListURL();
    }

    @Override
    public String getAssetBaseURL() {
        return fileProvider.getAssetBaseURL();
    }

    @Override
    public String injectURL(String baseURL) {
        return fileProvider.injectURL(baseURL);
    }

    @Override
    public VersionList<?> getVersionListById(String id) {
        return versionListProvider.getVersionListById(id);
    }

    @Override
    public int getConcurrency() {
        return fileProvider.getConcurrency();
    }
}
