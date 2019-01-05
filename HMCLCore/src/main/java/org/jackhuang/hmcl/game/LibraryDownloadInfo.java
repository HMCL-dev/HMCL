/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Immutable;

/**
 *
 * @author huangyuhui
 */
@Immutable
public class LibraryDownloadInfo extends DownloadInfo {

    @SerializedName("path")
    private final String path;

    public LibraryDownloadInfo() {
        this(null);
    }

    public LibraryDownloadInfo(String path) {
        this(path, "");
    }

    public LibraryDownloadInfo(String path, String url) {
        this(path, url, null);
    }

    public LibraryDownloadInfo(String path, String url, String sha1) {
        this(path, url, sha1, 0);
    }

    public LibraryDownloadInfo(String path, String url, String sha1, int size) {
        super(url, sha1, size);
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
