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
package org.jackhuang.hmcl.addon.meta;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.versioning.GameVersionNumber.atLeast;
import static org.jackhuang.hmcl.util.versioning.GameVersionNumber.between;

public record CoreMod(ModLoaderType modLoaderType, VersionRange<GameVersionNumber> mcVersionRange) {

    @NotNull
    @Unmodifiable
    public static List<CoreMod> fromFile(Path modFile, ZipFileTree tree) {
        if (!"jar".equalsIgnoreCase(FileUtils.getExtension(modFile))) return List.of();
        List<CoreMod> coreMods = new ArrayList<>();
        {
            // Below 1.13
            ZipArchiveEntry mf = tree.getEntry("META-INF/MANIFEST.MF");
            if (mf != null) {
                Attributes attr = null;
                try (var in = tree.getInputStream(mf)) {
                    attr = new Manifest(in).getMainAttributes();
                } catch (IOException e) {
                    LOG.warning("Failed to load jar manifest for jar: " + modFile, e);
                }
                if (attr != null) {
                    String fmlCorePlg = attr.getValue("FMLCorePlugin");
                    String tweakClass = attr.getValue("TweakClass");
                    if (StringUtils.isNotBlank(fmlCorePlg) || StringUtils.isNotBlank(tweakClass)) {
                        coreMods.add(new CoreMod(ModLoaderType.FORGE, between("1.6.1", "1.12.2")));
                    }
                }
            }
        }
        {
            // coremods.json in Forge 1.13-1.21.10 & NeoForge 1.21.4-
            // TODO Find a sample
            ZipArchiveEntry coreModsJson = tree.getEntry("META-INF/coremods.json");
            if (coreModsJson != null) {
                try (var in = new InputStreamReader(tree.getInputStream(coreModsJson))) {
                    var map = JsonUtils.fromJson(in, new TypeToken<Map<String, String>>() {
                    });
                    if (map != null && !map.isEmpty())
                        coreMods.addAll(List.of(
                                // Removed in https://github.com/MinecraftForge/MinecraftForge/pull/10746
                                new CoreMod(ModLoaderType.FORGE, between("1.13", "1.21.10")),
                                // Removed in https://github.com/neoforged/NeoForge/pull/2072
                                new CoreMod(ModLoaderType.NEO_FORGE, between("1.20.1", "1.21.4"))
                        ));
                } catch (IOException | JsonIOException e) {
                    LOG.warning("Failed to read coremods.json for jar: " + modFile, e);
                } catch (JsonSyntaxException ignored) {
                }
            }
        }
        {
            // ITransformationService
            if (tree.getEntry("META-INF/services/cpw.mods.modlauncher.api.ITransformationService") != null)
                coreMods.addAll(List.of(
                        new CoreMod(ModLoaderType.FORGE, atLeast("1.13.2")),
                        new CoreMod(ModLoaderType.NEO_FORGE, between("1.20.1", "1.21.8")) // Replaced by ClassProcessorProvider
                ));
        }
        {
            // ICoreMod for NeoForge
            // https://github.com/neoforged/FancyModLoader/pull/79
            // https://neoforged.net/news/2024-retrospection/#the-changes
            // TODO Find a sample
            if (tree.getEntry("META-INF/services/net.neoforged.neoforgespi.coremod.ICoreMod") != null)
                coreMods.add(new CoreMod(ModLoaderType.NEO_FORGE, between("1.20.5", "1.21.8"))); // Replaced by ClassProcessorProvider
        }
        {
            // ClassProcessorProvider for NeoForge, replaces ITransformationService & ICoreMod
            // https://github.com/neoforged/NeoForge/pull/2655
            // https://github.com/neoforged/FancyModLoader/pull/358
            if (tree.getEntry("META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessorProvider") != null)
                coreMods.add(new CoreMod(ModLoaderType.NEO_FORGE, atLeast("1.21.9")));
        }
        return List.copyOf(coreMods);
    }

    @NotNull
    @Unmodifiable
    public static Set<ModLoaderType> getSupportedLoaders(List<CoreMod> coreMods, @Nullable String gameVersion) {
        Set<ModLoaderType> supportedLoaders = EnumSet.noneOf(ModLoaderType.class);
        var version = GameVersionNumber.asGameVersion(Optional.ofNullable(gameVersion));
        if (version == GameVersionNumber.unknown()) return Set.of();
        for (var coreMod : coreMods)
            if (coreMod.mcVersionRange().contains(version))
                supportedLoaders.add(coreMod.modLoaderType());
        return Set.copyOf(supportedLoaders);
    }

}
