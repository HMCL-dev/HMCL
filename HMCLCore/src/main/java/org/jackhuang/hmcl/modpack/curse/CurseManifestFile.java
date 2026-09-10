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

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.Pair.pair;

/// @author huangyuhui
@JsonSerializable
public record CurseManifestFile(@SerializedName("projectID") int projectID,
                                @SerializedName("fileID") int fileID,
                                @SerializedName("fileName") String fileName,
                                @SerializedName("url") String url,
                                @SerializedName("required") boolean required,
                                @SerializedName("hashes") Map<String, String> hashes) implements Validation {

    private static final List<Pair<String, String>> HASH_ALGORITHMS = List.of(
            pair("sha1", "SHA-1"),
            pair("sha256", "SHA-256"),
            pair("sha512", "SHA-512"),
            pair("md5", "MD5")
    );

    @Override
    public void validate() throws JsonParseException {
        if (projectID == 0 || fileID == 0)
            throw new JsonParseException("Missing Project ID or File ID.");
    }

    @Override
    @Nullable
    public String url() {
        if (url == null) {
            return fileName != null
                    ? String.format("https://edge.forgecdn.net/files/%d/%d/%s", fileID / 1000, fileID % 1000, fileName)
                    : null;
        } else {
            return url;
        }
    }

    @Nullable
    public FileDownloadTask.IntegrityCheck getIntegrityCheck() {
        if (hashes == null || hashes.isEmpty()) return null;

        for (Pair<String, String> algorithm : HASH_ALGORITHMS) {
            String hash = hashes.get(algorithm.key());
            if (hash != null) {
                return new FileDownloadTask.IntegrityCheck(algorithm.value(), hash);
            }
        }
        return null;
    }

    public CurseManifestFile withFileName(String fileName) {
        return new CurseManifestFile(projectID, fileID, fileName, url, required, hashes);
    }

    public CurseManifestFile withURL(String url) {
        return new CurseManifestFile(projectID, fileID, fileName, url, required, hashes);
    }

    public CurseManifestFile withHashes(Map<String, String> hashes) {
        return new CurseManifestFile(projectID, fileID, fileName, url, required, hashes);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof CurseManifestFile that
                && this.projectID == that.projectID
                && this.fileID == that.fileID;
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectID, fileID);
    }
}
