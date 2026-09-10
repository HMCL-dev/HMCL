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
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// A CurseForge modpack file entry.
///
/// @author huangyuhui
@JsonSerializable
public record CurseManifestFile(
        @SerializedName("projectID") int projectID,
        @SerializedName("fileID") int fileID,
        @SerializedName("fileName") @Nullable String fileName,
        @SerializedName("url") @Nullable String url,
        @SerializedName("required") boolean required,
        @Nullable RemoteAddon remoteAddon,
        boolean addonQueried) implements Validation, ModpackFile {

    /// Creates a file entry without remote addon metadata.
    ///
    /// @param projectID the project id
    /// @param fileID    the file id
    /// @param fileName  the file name, or `null`
    /// @param url       the download URL, or `null`
    /// @param required  whether the file is required
    public CurseManifestFile(int projectID, int fileID, @Nullable String fileName, @Nullable String url, boolean required) {
        this(projectID, fileID, fileName, url, required, null, false);
    }

    @Override
    public String key() {
        return "curseforge:" + projectID + ":" + fileID;
    }

    @Override
    public boolean optional() {
        return !required();
    }

    @Override
    public @Nullable String path() {
        return fileName != null ? "mods/" + fileName : null;
    }

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

    /// Returns a copy with a resolved file name.
    ///
    /// @param fileName the resolved file name
    /// @return the updated entry
    public CurseManifestFile withFileName(String fileName) {
        return new CurseManifestFile(projectID, fileID, fileName, url, required, remoteAddon, addonQueried);
    }

    /// Returns a copy with a resolved download URL.
    ///
    /// @param url the download URL
    /// @return the updated entry
    public CurseManifestFile withURL(String url) {
        return new CurseManifestFile(projectID, fileID, fileName, url, required, remoteAddon, addonQueried);
    }

    /// Returns a copy marked as queried, with the given remote addon metadata.
    ///
    /// @param remoteAddon the remote addon, or `null` when not found
    /// @return the updated entry
    public CurseManifestFile withAddon(@Nullable RemoteAddon remoteAddon) {
        return new CurseManifestFile(projectID, fileID, fileName, url, required, remoteAddon, true);
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
