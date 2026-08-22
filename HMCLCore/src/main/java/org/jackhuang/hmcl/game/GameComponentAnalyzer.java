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
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionRange;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.regex.Pattern;

@NotNullByDefault
public final class GameComponentAnalyzer implements Iterable<GameComponentAnalyzer.Mark> {

    private static GameComponentAnalyzer analyze(
            GameInstanceManifest standaloneManifest,
            GameInstanceManifest launchManifest,
            @Nullable GameVersionNumber gameVersion) {
        var components = new EnumMap<GameComponentType, Mark>(GameComponentType.class);
        @Nullable String bootstrapVersion = null;

        if (gameVersion != null && !gameVersion.equals(GameVersionNumber.unknown())) {
            components.put(GameComponentType.GAME, new Mark(GameComponentType.GAME, gameVersion.toString(), true));
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

            if (bootstrapVersion == null && library.is("cpw.mods", "bootstraplauncher")) {
                bootstrapVersion = library.version();
            }
        }

        return new GameComponentAnalyzer(standaloneManifest, components, bootstrapVersion);
    }

    public static GameComponentAnalyzer analyze(GameInstanceManifest.Resolved resolved, @Nullable GameVersionNumber gameVersion) {
        return analyze(resolved.standaloneManifest(), resolved.launchManifest(), gameVersion);
    }

    public static GameComponentAnalyzer analyze(GameInstanceManifest manifest, @Nullable GameVersionNumber gameVersion) {
        if (manifest.inheritsFrom() != null)
            throw new IllegalArgumentException("LibraryAnalyzer can only analyze independent game version");

        return analyze(manifest, manifest, gameVersion);
    }

    private final GameInstanceManifest manifest;
    private final @Unmodifiable Map<GameComponentType, Mark> components;
    private final @Nullable String bootstrapVersion;

    private GameComponentAnalyzer(GameInstanceManifest manifest, @Unmodifiable Map<GameComponentType, Mark> components, @Nullable String bootstrapVersion) {
        this.manifest = manifest;
        this.components = components;
        this.bootstrapVersion = bootstrapVersion;
    }

    public boolean has(GameComponentType type) {
        return components.containsKey(type);
    }

    public boolean has(ModLoaderType type) {
        for (GameComponentType componentType : components.keySet()) {
            if (componentType.getModLoaderType() == type) {
                return true;
            }
        }
        return false;
    }

    public boolean hasModLauncher() {
        return GameComponentAnalyzer.MOD_LAUNCHER_MAIN.equals(manifest.mainClass()) || manifest.getPatches().stream().anyMatch(
                patch -> GameComponentAnalyzer.MOD_LAUNCHER_MAIN.equals(patch.mainClass())
        );
    }

    private static GameInstanceManifest removingMatchedLibrary(GameInstanceManifest manifest, GameComponentType type) {
        List<Library> libraries = new ArrayList<>();
        List<Library> rawLibraries = manifest.getLibraries();
        for (Library library : rawLibraries) {
            if (type.matchLibrary(library, rawLibraries)) {
                // skip
            } else {
                libraries.add(library);
            }
        }
        return manifest.withLibraries(libraries);
    }

    private GameInstancePatch removingMatchedLibrary(GameInstancePatch patch, GameComponentType type) {
        List<Library> libraries = new ArrayList<>();
        List<Library> rawLibraries = patch.getLibraries();
        for (Library library : rawLibraries) {
            if (type.matchLibrary(library, rawLibraries)) {
                // skip
            } else {
                libraries.add(library);
            }
        }
        return patch.withLibraries(libraries);
    }

    /// Remove library by library id
    ///
    /// @param componentType the patch identifier, such as `forge`, `optifine`, or `fabric`
    /// @return this
    public GameInstanceManifest removeLibrary(GameComponentType componentType) {
        if (!has(componentType)) return manifest;
        GameInstanceManifest manifest = removingMatchedLibrary(this.manifest, componentType);
        return manifest.withPatches(this.manifest.getPatches().stream()
                .filter(patch -> !componentType.getPatchId().equals(patch.id()))
                .map(patch -> removingMatchedLibrary(patch, componentType))
                .toList());
    }

    public @Nullable Mark getMark(GameComponentType type) {
        return components.get(type);
    }

    public @Nullable String getVersion(GameComponentType type) {
        Mark mark = components.get(type);
        return mark != null ? mark.version() : null;
    }

    public @Nullable String getBootstrapVersion() {
        return bootstrapVersion;
    }

    /// If a library is provided in `$.patches`, it's structure is so clear that we can do any operation.
    /// Otherwise, we must guess how are these libraries mixed.
    /// Maybe a guessing implementation will be provided in the future. But by now, we simply set it to JUST\_EXISTED.
    public boolean isClear(GameComponentType type) {
        return manifest.hasPatch(type.getPatchId());
    }

    @Override
    public Iterator<Mark> iterator() {
        return components.values().iterator();
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
            VANILLA_MAIN,
            LAUNCH_WRAPPER_MAIN,
            MOD_LAUNCHER_MAIN,
            BOOTSTRAP_LAUNCHER_MAIN,
            FORGE_BOOTSTRAP_MAIN,
            NEO_FORGE_BOOTSTRAP_MAIN
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

    public static final Pattern OPTIFINE_VERSION_PATTERN = Pattern.compile("^([0-9.]+)_(?<optifine>HD_.+)$");
}
