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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * @author huangyuhui
 */
public final class HMCLModpackManager {
    private HMCLModpackManager() {
    }

    /**
     * Read the manifest in a HMCL modpack.
     *
     * @param file     a HMCL modpack file.
     * @param encoding encoding of modpack zip file.
     * @return the manifest of HMCL modpack.
     * @throws IOException        if the file is not a valid zip file.
     * @throws JsonParseException if the manifest.json is missing or malformed.
     */
    public static Modpack readHMCLModpackManifest(Path file, Charset encoding) throws IOException, JsonParseException {
        String manifestJson = CompressingUtils.readTextZipEntry(file, "modpack.json", encoding);
        Modpack manifest = JsonUtils.fromNonNullJson(manifestJson, Modpack.class).setEncoding(encoding);
        String gameJson = CompressingUtils.readTextZipEntry(file, "minecraft/pack.json", encoding);
        Version game = JsonUtils.fromNonNullJson(gameJson, Version.class);
        if (game.getJar() == null)
            if (StringUtils.isBlank(manifest.getVersion()))
                throw new JsonParseException("Cannot recognize the game version of modpack " + file + ".");
            else
                return manifest.setManifest(HMCLModpackManifest.INSTANCE);
        else
            return manifest.setManifest(HMCLModpackManifest.INSTANCE).setGameVersion(game.getJar());
    }
}
