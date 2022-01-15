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
package org.jackhuang.hmcl.mod.curse;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.Validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class CurseManifestMinecraft implements Validation {

    @SerializedName("version")
    private final String gameVersion;

    @SerializedName("modLoaders")
    private final List<CurseManifestModLoader> modLoaders;

    public CurseManifestMinecraft() {
        this.gameVersion = "";
        this.modLoaders = Collections.emptyList();
    }

    public CurseManifestMinecraft(String gameVersion, List<CurseManifestModLoader> modLoaders) {
        this.gameVersion = gameVersion;
        this.modLoaders = new ArrayList<>(modLoaders);
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public List<CurseManifestModLoader> getModLoaders() {
        return Collections.unmodifiableList(modLoaders);
    }

    @Override
    public void validate() throws JsonParseException {
        if (StringUtils.isBlank(gameVersion))
            throw new JsonParseException("CurseForge Manifest.gameVersion cannot be blank.");
    }

}
