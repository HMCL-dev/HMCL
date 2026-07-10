/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.modpack.curse;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Immutable;

/**
 * CurseMetaMod is JSON structure for
 * https://cursemeta.dries007.net/&lt;projectID&gt;/&lt;fileID&gt;.json
 * https://addons-ecs.forgesvc.net/api/v2/addon/&lt;projectID&gt;/file/&lt;fileID&gt;
 */
@Immutable
public record CurseMetaMod(@SerializedName(value = "Id", alternate = "id") int id,
                           @SerializedName(value = "FileName", alternate = "fileName") String fileName,
                           @SerializedName(value = "FileNameOnDisk") String fileNameOnDisk,
                           @SerializedName(value = "DownloadURL", alternate = "downloadUrl") String downloadURL) {

    public CurseMetaMod {
        if (fileName == null) fileName = "";
        if (fileNameOnDisk == null) fileNameOnDisk = "";
        if (downloadURL == null) downloadURL = "";
    }
}
