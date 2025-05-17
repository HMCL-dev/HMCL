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
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
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
    private final AssetIndexInfo assetIndex;

    private final String minecraftArguments;

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

    public MultiMCInstancePatch(String name, String version, int order, AssetIndexInfo assetIndex, String minecraftArguments, String gameVersion, String mainClass, int[] javaMajors, @Nullable List<String> tweakers, @Nullable List<String> jvmArgs, @Nullable List<Library> _libraries, @Nullable List<Library> libraries, @Nullable List<Library> jarMods) {
        this.name = name;
        this.version = version;
        this.order = order;
        this.assetIndex = assetIndex;
        this.minecraftArguments = minecraftArguments;
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

    public AssetIndexInfo getAssetIndex() {
        return assetIndex;
    }

    public String getMinecraftArguments() {
        return minecraftArguments;
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

    public Version asVersion(String patchID) {
        List<String> arguments = new ArrayList<>();
        for (String arg : getTweakers()) {
            arguments.add("--tweakClass");
            arguments.add(arg);
        }

        Version version = new Version(patchID)
                .setVersion(getVersion())
                .setArguments(new Arguments().addGameArguments(arguments).addJVMArguments(getJvmArgs()))
                .setMainClass(getMainClass())
                .setMinecraftArguments(getMinecraftArguments())
                .setLibraries(getLibraries())
                .setAssetIndex(getAssetIndex());

        /* TODO: Official Version Json can only store one GameJavaVersion, not a array of all suitable java versions.
            For compatibility with official launcher and any other launchers, a transform is made between int[] and GameJavaVersion. */
        int[] majors = getJavaMajors();
        if (majors != null) {
            majors = majors.clone();
            Arrays.sort(majors);

            for (int i = majors.length - 1; i >= 0; i--) {
                GameJavaVersion jv = GameJavaVersion.get(majors[i]);
                if (jv != null) {
                    version = version.setJavaVersion(jv);
                    break;
                }
            }
        }

        return version;
    }
}
