/*
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

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.Lang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class MultiMCInstancePatch {

    private final String name;
    private final String version;

    @SerializedName("mcVersion")
    private final String gameVersion;
    private final String mainClass;
    private final String fileId;

    @SerializedName("+tweakers")
    private final List<String> tweakers;

    @SerializedName("+libraries")
    private final List<Library> _libraries;

    @SerializedName("libraries")
    private final List<Library> libraries;

    public MultiMCInstancePatch() {
        this("", "", "", "", "", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public MultiMCInstancePatch(String name, String version, String gameVersion, String mainClass, String fileId, List<String> tweakers, List<Library> _libraries, List<Library> libraries) {
        this.name = name;
        this.version = version;
        this.gameVersion = gameVersion;
        this.mainClass = mainClass;
        this.fileId = fileId;
        this.tweakers = new ArrayList<>(tweakers);
        this._libraries = new ArrayList<>(_libraries);
        this.libraries = new ArrayList<>(libraries);
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public String getMainClass() {
        return mainClass;
    }

    public String getFileId() {
        return fileId;
    }

    public List<String> getTweakers() {
        return Collections.unmodifiableList(tweakers);
    }

    public List<Library> getLibraries() {
        return Lang.merge(_libraries, libraries);
    }

}
