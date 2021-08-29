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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Official Download Provider fetches version list from Mojang and
 * download files from mcbbs.
 *
 * @author huangyuhui
 */
public class BalancedDownloadProvider implements DownloadProvider {
    List<DownloadProvider> candidates;

    VersionList<?> game, fabric, forge, liteLoader, optifine;

    public BalancedDownloadProvider(List<DownloadProvider> candidates) {
        this.candidates = candidates;

        this.game = new MultipleSourceVersionList(
                candidates.stream()
                        .map(downloadProvider -> downloadProvider.getVersionListById("game"))
                        .collect(Collectors.toList()));
        this.fabric = new MultipleSourceVersionList(
                candidates.stream()
                        .map(downloadProvider -> downloadProvider.getVersionListById("fabric"))
                        .collect(Collectors.toList()));
        this.forge = new MultipleSourceVersionList(
                candidates.stream()
                        .map(downloadProvider -> downloadProvider.getVersionListById("forge"))
                        .collect(Collectors.toList()));
        this.liteLoader = new MultipleSourceVersionList(
                candidates.stream()
                        .map(downloadProvider -> downloadProvider.getVersionListById("liteloader"))
                        .collect(Collectors.toList()));
        this.optifine = new MultipleSourceVersionList(
                candidates.stream()
                        .map(downloadProvider -> downloadProvider.getVersionListById("optifine"))
                        .collect(Collectors.toList()));
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
        switch (id) {
            case "game":
                return game;
            case "fabric":
                return fabric;
            case "forge":
                return forge;
            case "liteloader":
                return liteLoader;
            case "optifine":
                return optifine;
            default:
                throw new IllegalArgumentException("Unrecognized version list id: " + id);
        }
    }

    @Override
    public int getConcurrency() {
        throw new UnsupportedOperationException();
    }
}
