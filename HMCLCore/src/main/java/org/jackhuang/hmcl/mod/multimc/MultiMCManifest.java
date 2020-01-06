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
package org.jackhuang.hmcl.mod.multimc;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.IOUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Immutable
public final class MultiMCManifest {

    @SerializedName("formatVersion")
    private final int formatVersion;

    @SerializedName("components")
    private final List<MultiMCManifestComponent> components;

    public MultiMCManifest(int formatVersion, List<MultiMCManifestComponent> components) {
        this.formatVersion = formatVersion;
        this.components = components;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public List<MultiMCManifestComponent> getComponents() {
        return components;
    }

    /**
     * Read MultiMC modpack manifest from zip file
     * @param root root path in zip file (Path root is a path of ZipFileSystem)
     * @return the MultiMC modpack manifest.
     * @throws IOException if zip file is malformed
     * @throws com.google.gson.JsonParseException if manifest is malformed.
     */
    public static MultiMCManifest readMultiMCModpackManifest(Path root) throws IOException {
        Path mmcPack = root.resolve("mmc-pack.json");
        if (Files.notExists(mmcPack))
            return null;
        String json = IOUtils.readFullyAsString(Files.newInputStream(mmcPack));
        MultiMCManifest manifest = JsonUtils.fromNonNullJson(json, MultiMCManifest.class);

        if (manifest.getComponents() == null)
            throw new IOException("mmc-pack.json malformed.");

        return manifest;
    }

    public static final class MultiMCManifestCachedRequires {
        @SerializedName("equals")
        private final String equalsVersion;

        @SerializedName("uid")
        private final String uid;

        @SerializedName("suggests")
        private final String suggests;



        public MultiMCManifestCachedRequires(String equalsVersion, String uid, String suggests) {
            this.equalsVersion = equalsVersion;
            this.uid = uid;
            this.suggests = suggests;
        }

        public String getEqualsVersion() {
            return equalsVersion;
        }

        public String getUid() {
            return uid;
        }

        public String getSuggests() {
            return suggests;
        }
    }

    public static final class MultiMCManifestComponent {
        @SerializedName("cachedName")
        private final String cachedName;

        @SerializedName("cachedRequires")
        private final List<MultiMCManifestCachedRequires> cachedRequires;

        @SerializedName("cachedVersion")
        private final String cachedVersion;

        @SerializedName("important")
        private final boolean important;

        @SerializedName("dependencyOnly")
        private final boolean dependencyOnly;

        @SerializedName("uid")
        private final String uid;

        @SerializedName("version")
        private final String version;

        public MultiMCManifestComponent(boolean important, boolean dependencyOnly, String uid, String version) {
            this(null, null, null, important, dependencyOnly, uid, version);
        }

        public MultiMCManifestComponent(String cachedName, List<MultiMCManifestCachedRequires> cachedRequires, String cachedVersion, boolean important, boolean dependencyOnly, String uid, String version) {
            this.cachedName = cachedName;
            this.cachedRequires = cachedRequires;
            this.cachedVersion = cachedVersion;
            this.important = important;
            this.dependencyOnly = dependencyOnly;
            this.uid = uid;
            this.version = version;
        }

        public String getCachedName() {
            return cachedName;
        }

        public List<MultiMCManifestCachedRequires> getCachedRequires() {
            return cachedRequires;
        }

        public String getCachedVersion() {
            return cachedVersion;
        }

        public boolean isImportant() {
            return important;
        }

        public boolean isDependencyOnly() {
            return dependencyOnly;
        }

        public String getUid() {
            return uid;
        }

        public String getVersion() {
            return version;
        }
    }
}
