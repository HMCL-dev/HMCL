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
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author huangyuhui
 */
@Immutable
public final class MultiMCInstancePatch {

    private final String name;
    private final String version;
    private final int order;

    @SerializedName("mcVersion")
    private final String gameVersion;
    private final String mainClass;
    @SerializedName("compatibleJavaMajors")
    private final int[] javaMajors;

    @SerializedName("+tweakers")
    @Nullable
    private final List<String> tweakers;

    @SerializedName("+jvmArgs")
    @Nullable
    private final List<String> jvmArgs;

    @SerializedName("+libraries")
    @Nullable
    private final List<Library> _libraries;

    @SerializedName("libraries")
    @Nullable
    private final List<Library> libraries;

    @Nullable
    private final List<Library> jarMods;

    public MultiMCInstancePatch(String name, String version, int order, String gameVersion, String mainClass, int[] javaMajors, @Nullable List<String> tweakers, @Nullable List<String> jvmArgs, @Nullable List<Library> _libraries, @Nullable List<Library> libraries, @Nullable List<Library> jarMods) {
        this.name = name;
        this.version = version;
        this.order = order;
        this.gameVersion = gameVersion;
        this.mainClass = mainClass;
        this.javaMajors = javaMajors;
        this.tweakers = tweakers;
        this.jvmArgs = jvmArgs;
        this._libraries = _libraries;
        this.libraries = libraries;
        this.jarMods = jarMods;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public int getOrder() {
        return order;
    }

    public int[] getJavaMajors() {
        return javaMajors;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public String getMainClass() {
        return mainClass;
    }

    public List<String> getTweakers() {
        return tweakers != null ? Collections.unmodifiableList(tweakers) : Collections.emptyList();
    }

    public List<String> getJvmArgs() {
        return jvmArgs != null ? Collections.unmodifiableList(jvmArgs) : Collections.emptyList();
    }

    public List<Library> getLibraries() {
        return Lang.merge(_libraries, libraries);
    }

    public List<Library> getJarMods() {
        return jarMods != null ? Collections.unmodifiableList(jarMods) : Collections.emptyList();
    }
}
