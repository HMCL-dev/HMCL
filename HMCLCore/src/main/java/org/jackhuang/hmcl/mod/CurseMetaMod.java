/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Immutable;

@Immutable
public final class CurseMetaMod {
    @SerializedName("Id")
    private final int id;

    @SerializedName("FileName")
    private final String fileName;

    @SerializedName("FileNameOnDisk")
    private final String fileNameOnDisk;

    @SerializedName("DownloadURL")
    private final String downloadURL;

    public CurseMetaMod() {
        this(0, "", "", "");
    }

    public CurseMetaMod(int id, String fileName, String fileNameOnDisk, String downloadURL) {
        this.id = id;
        this.fileName = fileName;
        this.fileNameOnDisk = fileNameOnDisk;
        this.downloadURL = downloadURL;
    }

    public int getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileNameOnDisk() {
        return fileNameOnDisk;
    }

    public String getDownloadURL() {
        return downloadURL;
    }
}
