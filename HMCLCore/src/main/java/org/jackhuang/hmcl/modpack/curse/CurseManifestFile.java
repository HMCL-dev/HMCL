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
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.modpack.ModpackFile;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.Pair.pair;

/// A CurseForge modpack file entry.
///
/// @param projectID the project id
/// @param fileID the file id
/// @param fileName the file name, or `null` when unresolved
/// @param url the explicit download URL, or `null` to derive it from the file name
/// @param required whether the file is required
/// @param hashes the hash algorithm names and checksums, or `null` when unavailable;
///               the map is retained without copying
/// @param remoteAddon the remote addon metadata, or `null` when unavailable
/// @param addonQueried whether remote addon metadata has been queried
/// @author huangyuhui
@NotNullByDefault
@JsonSerializable
public record CurseManifestFile(
        @SerializedName("projectID") int projectID,
        @SerializedName("fileID") int fileID,
        @SerializedName("fileName") @Nullable String fileName,
        @SerializedName("url") @Nullable String url,
        @SerializedName("required") boolean required,
        @SerializedName("hashes") @Nullable Map<String, @Nullable String> hashes,
        @Nullable RemoteAddon remoteAddon,
        boolean addonQueried) implements Validation, ModpackFile {

    /// Supported manifest hash names and digest algorithms, in selection order.
    private static final @Unmodifiable List<Pair<String, String>> HASH_ALGORITHMS = List.of(
            pair("sha1", "SHA-1"),
            pair("sha256", "SHA-256"),
            pair("sha512", "SHA-512"),
            pair("md5", "MD5")
    );

    /// Creates a file entry without checksums or remote addon metadata.
    ///
    /// @param projectID the project id
    /// @param fileID    the file id
    /// @param fileName  the file name, or `null`
    /// @param url       the download URL, or `null`
    /// @param required  whether the file is required
    public CurseManifestFile(int projectID, int fileID, @Nullable String fileName, @Nullable String url, boolean required) {
        this(projectID, fileID, fileName, url, required, null, null, false);
    }

    /// Returns `curseforge:<projectID>:<fileID>` as the exclusion key.
    @Override
    public String key() {
        return "curseforge:" + projectID + ":" + fileID;
    }

    /// Returns whether this entry is not required by the manifest.
    @Override
    public boolean optional() {
        return !required();
    }

    /// Returns the file name prefixed with `mods/`, or `null` when unresolved.
    @Override
    public @Nullable String path() {
        return fileName != null ? "mods/" + fileName : null;
    }

    /// Checks that the project and file ids are nonzero.
    ///
    /// @throws JsonParseException if either id is zero
    @Override
    public void validate() throws JsonParseException {
        if (projectID == 0 || fileID == 0)
            throw new JsonParseException("Missing Project ID or File ID.");
    }

    /// Returns the download URL, deriving a ForgeCDN URL when the manifest omits it.
    ///
    /// @return the download URL, or `null` when the file name is also missing
    @Override
    public @Nullable String url() {
        if (url == null) {
            return fileName != null
                    ? String.format("https://edge.forgecdn.net/files/%d/%d/%s", fileID / 1000, fileID % 1000, fileName)
                    : null;
        } else {
            return url;
        }
    }

    /// Returns an integrity check using the first non-null checksum under the keys
    /// `sha1`, `sha256`, `sha512`, and `md5`, in that order.
    ///
    /// @return the integrity check, or `null` when no supported checksum is available
    public @Nullable FileDownloadTask.IntegrityCheck getIntegrityCheck() {
        if (hashes == null || hashes.isEmpty()) return null;

        for (Pair<String, String> algorithm : HASH_ALGORITHMS) {
            @Nullable String hash = hashes.get(algorithm.key());
            if (hash != null) {
                return new FileDownloadTask.IntegrityCheck(algorithm.value(), hash);
            }
        }
        return null;
    }

    /// Returns a copy with a resolved file name, retaining all other fields.
    ///
    /// @param fileName the resolved file name
    /// @return the updated entry
    public CurseManifestFile withFileName(String fileName) {
        return new CurseManifestFile(projectID, fileID, fileName, url, required, hashes, remoteAddon, addonQueried);
    }

    /// Returns a copy with a resolved download URL, retaining all other fields.
    ///
    /// @param url the download URL
    /// @return the updated entry
    public CurseManifestFile withURL(String url) {
        return new CurseManifestFile(projectID, fileID, fileName, url, required, hashes, remoteAddon, addonQueried);
    }

    /// Returns a copy marked as queried, with the given remote addon metadata.
    ///
    /// @param remoteAddon the remote addon, or `null` when not found
    /// @return the updated entry, retaining all other fields
    public CurseManifestFile withAddon(@Nullable RemoteAddon remoteAddon) {
        return new CurseManifestFile(projectID, fileID, fileName, url, required, hashes, remoteAddon, true);
    }

    /// Returns a copy with the given checksums, retaining all other fields.
    ///
    /// @param hashes the hash algorithm names and checksums, retained without copying;
    ///               `null` removes the checksums
    /// @return the updated entry
    public CurseManifestFile withHashes(@Nullable Map<String, @Nullable String> hashes) {
        return new CurseManifestFile(projectID, fileID, fileName, url, required, hashes, remoteAddon, addonQueried);
    }

    /// Returns whether the other object is a file entry with the same project and file ids.
    @Override
    public boolean equals(@Nullable Object o) {
        return this == o || o instanceof CurseManifestFile that
                && this.projectID == that.projectID
                && this.fileID == that.fileID;
    }

    /// Returns a hash code based only on the project and file ids.
    @Override
    public int hashCode() {
        return Objects.hash(projectID, fileID);
    }
}
