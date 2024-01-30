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
import java.util.Map;

/**
 * Official Download Provider fetches version list from Mojang and
 * download files from mcbbs.
 *
 * @author huangyuhui
 */
public final class BalancedDownloadProvider implements DownloadProvider {
    private final DownloadProvider[] candidates;
    private final Map<String, VersionList<?>> versionLists = new HashMap<>();

    public BalancedDownloadProvider(DownloadProvider... candidates) {
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
        return versionLists.computeIfAbsent(id, value -> {
            VersionList<?>[] lists = new VersionList<?>[candidates.length];
            for (int i = 0; i < candidates.length; i++) {
                lists[i] = candidates[i].getVersionListById(value);
            }
            return new MultipleSourceVersionList(lists);
        });
    }

    @Override
    public int getConcurrency() {
        throw new UnsupportedOperationException();
    }
}
