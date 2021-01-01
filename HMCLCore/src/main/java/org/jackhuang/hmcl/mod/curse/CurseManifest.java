/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class CurseManifest {

    @SerializedName("manifestType")
    private final String manifestType;

    @SerializedName("manifestVersion")
    private final int manifestVersion;

    @SerializedName("name")
    private final String name;

    @SerializedName("version")
    private final String version;

    @SerializedName("author")
    private final String author;

    @SerializedName("overrides")
    private final String overrides;

    @SerializedName("minecraft")
    private final CurseManifestMinecraft minecraft;

    @SerializedName("files")
    private final List<CurseManifestFile> files;

    public CurseManifest() {
        this(MINECRAFT_MODPACK, 1, "", "1.0", "", "overrides", new CurseManifestMinecraft(), Collections.emptyList());
    }

    public CurseManifest(String manifestType, int manifestVersion, String name, String version, String author, String overrides, CurseManifestMinecraft minecraft, List<CurseManifestFile> files) {
        this.manifestType = manifestType;
        this.manifestVersion = manifestVersion;
        this.name = name;
        this.version = version;
        this.author = author;
        this.overrides = overrides;
        this.minecraft = minecraft;
        this.files = files;
    }

    public String getManifestType() {
        return manifestType;
    }

    public int getManifestVersion() {
        return manifestVersion;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public String getOverrides() {
        return overrides;
    }

    public CurseManifestMinecraft getMinecraft() {
        return minecraft;
    }

    public List<CurseManifestFile> getFiles() {
        return files;
    }

    public CurseManifest setFiles(List<CurseManifestFile> files) {
        return new CurseManifest(manifestType, manifestVersion, name, version, author, overrides, minecraft, files);
    }

    /**
     * @param zip the CurseForge modpack file.
     * @throws IOException if the file is not a valid zip file.
     * @throws JsonParseException if the manifest.json is missing or malformed.
     * @return the manifest.
     */
    public static Modpack readCurseForgeModpackManifest(Path zip, Charset encoding) throws IOException, JsonParseException {
        String json = CompressingUtils.readTextZipEntry(zip, "manifest.json", encoding);
        CurseManifest manifest = JsonUtils.fromNonNullJson(json, CurseManifest.class);
        return new Modpack(manifest.getName(), manifest.getAuthor(), manifest.getVersion(), manifest.getMinecraft().getGameVersion(),
                CompressingUtils.readTextZipEntryQuietly(zip, "modlist.html", encoding).orElse( "No description"), encoding, manifest);
    }

    public static final String MINECRAFT_MODPACK = "minecraftModpack";
}
