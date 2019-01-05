/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class ForgeModMetadata {

    @SerializedName("modid")
    private final String modId;
    private final String name;
    private final String description;
    private final String author;
    private final String version;
    private final String mcversion;
    private final String url;
    private final String updateUrl;
    private final String credits;
    private final String[] authorList;
    private final String[] authors;

    public ForgeModMetadata() {
        this("", "", "", "", "", "", "", "", "", new String[0], new String[0]);
    }

    public ForgeModMetadata(String modId, String name, String description, String author, String version, String mcversion, String url, String updateUrl, String credits, String[] authorList, String[] authors) {
        this.modId = modId;
        this.name = name;
        this.description = description;
        this.author = author;
        this.version = version;
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

    public static ModInfo fromFile(File modFile) throws IOException, JsonParseException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(modFile.toPath())) {
            Path mcmod = fs.getPath("mcmod.info");
            if (Files.notExists(mcmod))
                throw new IOException("File " + modFile + " is not a Forge mod.");
            List<ForgeModMetadata> modList = JsonUtils.GSON.fromJson(IOUtils.readFullyAsString(Files.newInputStream(mcmod)),
                    new TypeToken<List<ForgeModMetadata>>() {
                    }.getType());
            if (modList == null || modList.isEmpty())
                throw new IOException("Mod " + modFile + " `mcmod.info` is malformed..");
            ForgeModMetadata metadata = modList.get(0);
            String authors = metadata.getAuthor();
            if (StringUtils.isBlank(authors) && metadata.getAuthors().length > 0)
                authors = String.join(", ", metadata.getAuthors());
            if (StringUtils.isBlank(authors) && metadata.getAuthorList().length > 0)
                authors = String.join(", ", metadata.getAuthorList());
            if (StringUtils.isBlank(authors))
                authors = metadata.getCredits();
            return new ModInfo(modFile, metadata.getName(), metadata.getDescription(),
                    authors, metadata.getVersion(), metadata.getGameVersion(),
                    StringUtils.isBlank(metadata.getUrl()) ? metadata.getUpdateUrl() : metadata.url);
        }
    }
}
