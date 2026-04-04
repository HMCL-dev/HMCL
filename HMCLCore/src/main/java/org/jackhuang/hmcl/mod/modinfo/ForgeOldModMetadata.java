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
package org.jackhuang.hmcl.mod.modinfo;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class ForgeOldModMetadata {
    @SerializedName("modid")
    private final String modId;
    private final String name;
    private final String description;
    private final String author;
    private final String version;
    private final String logoFile;
    private final String mcversion;
    private final String url;
    private final String updateUrl;
    private final String credits;
    private final String[] authorList;
    private final String[] authors;

    public ForgeOldModMetadata() {
        this("", "", "", "", "", "", "", "", "", "", new String[0], new String[0]);
    }

    public ForgeOldModMetadata(String modId, String name, String description, String author, String version, String logoFile, String mcversion, String url, String updateUrl, String credits, String[] authorList, String[] authors) {
        this.modId = modId;
        this.name = name;
        this.description = description;
        this.author = author;
        this.version = version;
        this.logoFile = logoFile;
        this.mcversion = mcversion;
        this.url = url;
        this.updateUrl = updateUrl;
        this.credits = credits;
        this.authorList = authorList;
        this.authors = authors;
    }

    public String getModId() {
        return modId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthor() {
        return author;
    }

    public String getVersion() {
        return version;
    }

    public String getLogoFile() {
        return logoFile;
    }

    public String getGameVersion() {
        return mcversion;
    }

    public String getUrl() {
        return url;
    }

    public String getUpdateUrl() {
        return updateUrl;
    }

    public String getCredits() {
        return credits;
    }

    public String[] getAuthorList() {
        return authorList;
    }

    public String[] getAuthors() {
        return authors;
    }

    public static LocalModFile fromFile(ModManager modManager, Path modFile, ZipFileTree tree) throws IOException, JsonParseException {
        ZipArchiveEntry mcmod = tree.getEntry("mcmod.info");
        if (mcmod == null)
            throw new IOException("File " + modFile + " is not a Forge mod.");

        List<ForgeOldModMetadata> modList;

        try (var reader = tree.getBufferedReader(mcmod);
             var jsonReader = new JsonReader(reader)) {
            JsonToken firstToken = jsonReader.peek();

            if (firstToken == JsonToken.BEGIN_ARRAY)
                modList = JsonUtils.GSON.fromJson(jsonReader, listTypeOf(ForgeOldModMetadata.class));
            else if (firstToken == JsonToken.BEGIN_OBJECT) {
                ForgeOldModMetadataLst list = JsonUtils.GSON.fromJson(jsonReader, ForgeOldModMetadataLst.class);
                if (list == null)
                    throw new IOException("Mod " + modFile + " `mcmod.info` is malformed");
                modList = list.modList();
            } else {
                throw new JsonParseException("Unexpected first token: " + firstToken);
            }
        }

        if (modList == null || modList.isEmpty())
            throw new IOException("Mod " + modFile + " `mcmod.info` is malformed");
        ForgeOldModMetadata metadata = modList.get(0);
        String authors = metadata.getAuthor();
        if (StringUtils.isBlank(authors) && metadata.getAuthors().length > 0)
            authors = String.join(", ", metadata.getAuthors());
        if (StringUtils.isBlank(authors) && metadata.getAuthorList().length > 0)
            authors = String.join(", ", metadata.getAuthorList());
        if (StringUtils.isBlank(authors))
            authors = metadata.getCredits();
        return new LocalModFile(modManager, modManager.getLocalMod(metadata.getModId(), ModLoaderType.FORGE), modFile, metadata.getName(), new LocalModFile.Description(metadata.getDescription()),
                authors, metadata.getVersion(), metadata.getGameVersion(),
                StringUtils.isBlank(metadata.getUrl()) ? metadata.getUpdateUrl() : metadata.url,
                metadata.getLogoFile());
    }
}
