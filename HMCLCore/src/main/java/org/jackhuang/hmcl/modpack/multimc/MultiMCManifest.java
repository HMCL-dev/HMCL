// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.multimc;

import com.google.gson.annotations.SerializedName;
import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
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
     * @param zipFile the zip file
     * @return the MultiMC modpack manifest.
     * @throws IOException if zip file is malformed
     * @throws com.google.gson.JsonParseException if manifest is malformed.
     */
    public static MultiMCManifest readMultiMCModpackManifest(ZipArchiveReader zipFile, String rootEntryName) throws IOException {
        ZipArchiveEntry mmcPack = zipFile.getEntry(rootEntryName + "mmc-pack.json");
        if (mmcPack == null)
            return null;
        MultiMCManifest manifest = JsonUtils.fromNonNullJsonFully(zipFile.getInputStream(mmcPack), MultiMCManifest.class);
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

        public String getID() {
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
