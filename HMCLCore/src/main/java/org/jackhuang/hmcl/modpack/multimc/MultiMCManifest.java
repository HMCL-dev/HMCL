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
package org.jackhuang.hmcl.modpack.multimc;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.util.List;

@Immutable
public record MultiMCManifest(@SerializedName("formatVersion") int formatVersion,
                              @SerializedName("components") List<MultiMCManifestComponent> components) {

    /**
     * Read MultiMC modpack manifest from zip file
     *
     * @param zipFile the zip file
     * @return the MultiMC modpack manifest.
     * @throws IOException        if zip file is malformed
     * @throws JsonParseException if manifest is malformed.
     */
    public static MultiMCManifest readMultiMCModpackManifest(ZipArchiveReader zipFile, String rootEntryName) throws IOException {
        ZipArchiveEntry mmcPack = zipFile.getEntry(rootEntryName + "mmc-pack.json");
        if (mmcPack == null)
            return null;
        MultiMCManifest manifest = JsonUtils.fromNonNullJsonFully(zipFile.getInputStream(mmcPack), MultiMCManifest.class);
        if (manifest.components() == null)
            throw new IOException("mmc-pack.json malformed.");

        return manifest;
    }

    public record MultiMCManifestCachedRequires(@SerializedName("equals") String equalsVersion,
                                                @SerializedName("uid") String uid,
                                                @SerializedName("suggests") String suggests) {
    }

    public record MultiMCManifestComponent(@SerializedName("cachedName") String cachedName,
                                           @SerializedName("cachedRequires") List<MultiMCManifestCachedRequires> cachedRequires,
                                           @SerializedName("cachedVersion") String cachedVersion,
                                           @SerializedName("important") boolean important,
                                           @SerializedName("dependencyOnly") boolean dependencyOnly,
                                           @SerializedName("uid") String uid,
                                           @SerializedName("version") String version) {

        public MultiMCManifestComponent(boolean important, boolean dependencyOnly, String uid, String version) {
            this(null, null, null, important, dependencyOnly, uid, version);
        }
    }
}
