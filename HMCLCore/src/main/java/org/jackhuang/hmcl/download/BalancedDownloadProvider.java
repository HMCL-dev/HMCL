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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Official Download Provider fetches version list from Mojang and
 * download files from mcbbs.
 *
 * @author huangyuhui
 */
public class BalancedDownloadProvider implements DownloadProvider {
    List<DownloadProvider> candidates;

    Map<String, VersionList<?>> versionLists = new HashMap<>();

    public BalancedDownloadProvider(List<DownloadProvider> candidates) {
        this.candidates = candidates;
    }

    @Override
    public String getVersionListURL() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAssetBaseURL() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String injectURL(String baseURL) {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionList<?> getVersionListById(String id) {
        if (!versionLists.containsKey(id)) {
            versionLists.put(id, new MultipleSourceVersionList(
                    candidates.stream()
                            .map(downloadProvider -> downloadProvider.getVersionListById(id))
                            .collect(Collectors.toList())));
        }
        return versionLists.get(id);
    }

    @Override
    public int getConcurrency() {
        throw new UnsupportedOperationException();
    }
}
