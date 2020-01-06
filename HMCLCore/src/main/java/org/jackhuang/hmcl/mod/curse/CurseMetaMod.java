/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod.curse;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Immutable;

/**
 * CurseMetaMod is JSON structure for
 * https://cursemeta.dries007.net/&lt;projectID&gt;/&lt;fileID&gt;.json
 * https://addons-ecs.forgesvc.net/api/v2/addon/&lt;projectID&gt;/file/<fileID&gt;
 */
@Immutable
public final class CurseMetaMod {
    @SerializedName(value = "Id", alternate = "id")
    private final int id;

    @SerializedName(value = "FileName", alternate = "fileName")
    private final String fileName;

    @SerializedName(value = "FileNameOnDisk")
    private final String fileNameOnDisk;

    @SerializedName(value = "DownloadURL", alternate = "downloadUrl")
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
