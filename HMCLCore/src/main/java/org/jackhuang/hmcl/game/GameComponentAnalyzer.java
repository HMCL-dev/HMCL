/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionRange;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

@NotNullByDefault
public final class GameComponentAnalyzer implements Iterable<GameComponentAnalyzer.Mark> {

    private static GameComponentAnalyzer analyze(
            GameInstanceManifest standaloneManifest,
            GameInstanceManifest launchManifest,
            @Nullable String gameVersion) {
        var components = new EnumMap<GameComponentType, Mark>(GameComponentType.class);

        if (gameVersion != null) {
            components.put(GameComponentType.GAME, new Mark(GameComponentType.GAME, gameVersion, true));
        }

        for (GameInstancePatch patch : standaloneManifest.getPatches()) {
            if (patch.isHidden() || patch.id() == null) continue;

            @Nullable GameComponentType type = GameComponentType.fromPatchId(patch.id());
            if (type != null) {
                components.put(type, new Mark(type, patch.version(), true));
            }
        }

        List<Library> rawLibraries = launchManifest.getLibraries();
        for (Library library : rawLibraries) {
            for (GameComponentType type : GameComponentType.ALL) {
                if (components.containsKey(type)) continue;

                if (type.matchLibrary(library, rawLibraries)) {
                    components.put(type, new Mark(type, type.getComponentVersion(standaloneManifest, library.version()), false));
                    break;
                }
            }
        }

        return new GameComponentAnalyzer(standaloneManifest, components);
    }


    public static GameComponentAnalyzer analyze(GameInstanceManifest.Resolved resolved, @Nullable String gameVersion) {
        return analyze(resolved.standaloneManifest(), resolved.launchManifest(), gameVersion);
    }

    public static GameComponentAnalyzer analyze(GameInstanceManifest manifest, @Nullable String gameVersion) {
        if (manifest.inheritsFrom() != null)
            throw new IllegalArgumentException("LibraryAnalyzer can only analyze independent game version");

        return analyze(manifest, manifest, gameVersion);
    }

    private final GameInstanceManifest manifest;
    private final Map<GameComponentType, Mark> components;

    private GameComponentAnalyzer(GameInstanceManifest manifest, Map<GameComponentType, Mark> components) {
        this.manifest = manifest;
        this.components = components;
    }

    public boolean has(GameComponentType type) {
        return components.containsKey(type);
    }

    public @Nullable String getVersion(GameComponentType type) {
        Mark mark = components.get(type);
        return mark != null ? mark.version() : null;
    }

    /// If a library is provided in `$.patches`, it's structure is so clear that we can do any operation.
    /// Otherwise, we must guess how are these libraries mixed.
    /// Maybe a guessing implementation will be provided in the future. But by now, we simply set it to JUST\_EXISTED.
    public boolean isClear(GameComponentType type) {
        return manifest.hasPatch(type.getPatchId());
    }

    public @Unmodifiable Set<ModLoaderType> getModLoaders() {
        Set<ModLoaderType> res = EnumSet.noneOf(ModLoaderType.class);
        for (GameComponentType type : components.keySet()) {
            if (type.getModLoaderType() != null) {
                res.add(type.getModLoaderType());
            }
        }
        return res;
    }

    @Override
    public Iterator<Mark> iterator() {
        return components.values().iterator();
    }

    /// If a library is provided in `$.patches`, it's structure is so clear that we can do any operation.
    /// Otherwise, we must guess how are these libraries mixed.
    /// Maybe a guessing implementation will be provided in the future. But by now, we simply set it to JUST\_EXISTED.
    public enum Status {
        CLEAR, UNSURE, JUST_EXISTED
    }

    public record Mark(
            GameComponentType componentType,
            @Nullable String version,
            boolean clear
    ) {
    }

    public static final String VANILLA_MAIN = "net.minecraft.client.main.Main";
    public static final String LAUNCH_WRAPPER_MAIN = "net.minecraft.launchwrapper.Launch";
    public static final String MOD_LAUNCHER_MAIN = "cpw.mods.modlauncher.Launcher";
    public static final String BOOTSTRAP_LAUNCHER_MAIN = "cpw.mods.bootstraplauncher.BootstrapLauncher";
    public static final String FORGE_BOOTSTRAP_MAIN = "net.minecraftforge.bootstrap.ForgeBootstrap";
    public static final String NEO_FORGE_BOOTSTRAP_MAIN = "net.neoforged.fml.startup.Client";

    public static final Set<String> MOD_LOADER_MAIN_CLASSES_PACKAGES = Set.of(
            "net.minecraftforge",
            "net.neoforged",
            "top.outlands", // Cleanroom
            "net.fabricmc",
            "org.quiltmc",
            "cpw.mods"
    );

    public static final Set<String> FORGE_OPTIFINE_MAIN = Set.of(
            LibraryAnalyzer.VANILLA_MAIN,
            LibraryAnalyzer.LAUNCH_WRAPPER_MAIN,
            LibraryAnalyzer.MOD_LAUNCHER_MAIN,
            LibraryAnalyzer.BOOTSTRAP_LAUNCHER_MAIN,
            LibraryAnalyzer.FORGE_BOOTSTRAP_MAIN,
            LibraryAnalyzer.NEO_FORGE_BOOTSTRAP_MAIN
    );

    public static final VersionRange<VersionNumber> FORGE_OPTIFINE_BROKEN_RANGE = VersionNumber.between("48.0.0", "49.0.50");

    public static final @Unmodifiable List<String> FORGE_TWEAKERS = List.of(
            "net.minecraftforge.legacy._1_5_2.LibraryFixerTweaker", // 1.5.2
            "cpw.mods.fml.common.launcher.FMLTweaker", // 1.6.1 ~ 1.7.10
            "net.minecraftforge.fml.common.launcher.FMLTweaker" // 1.8 ~ 1.12.2
    );
    public static final @Unmodifiable List<String> OPTIFINE_TWEAKERS = List.of(
            "optifine.OptiFineTweaker",
            "optifine.OptiFineForgeTweaker"
    );
    public static final String LITELOADER_TWEAKER = "com.mumfrey.liteloader.launch.LiteLoaderTweaker";
}
