/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Immutable;

/**
 *
 * @author huangyuhui
 */
@Immutable
public class AssetIndexInfo extends IdDownloadInfo {

    private final long totalSize;

    public AssetIndexInfo() {
        this("", "");
    }

    public AssetIndexInfo(String id, String url) {
        this(id, url, null);
    }

    public AssetIndexInfo(String id, String url, String sha1) {
        this(id, url, sha1, 0);
    }

    public AssetIndexInfo(String id, String url, String sha1, int size) {
        this(id, url, sha1, size, 0);
    }

    public AssetIndexInfo(String id, String url, String sha1, int size, long totalSize) {
        super(id, url, sha1, size);
        this.totalSize = totalSize;
    }

    public long getTotalSize() {
        return totalSize;
    }
}
