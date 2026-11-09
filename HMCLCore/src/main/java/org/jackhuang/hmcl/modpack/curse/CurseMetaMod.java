// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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

    @Override
    public int id() {
        return id;
    }

    @Override
    public String fileName() {
        return fileName;
    }

    @Override
    public String fileNameOnDisk() {
        return fileNameOnDisk;
    }

    @Override
    public String downloadURL() {
        return downloadURL;
    }
}
