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

import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.versioning.GameVersionNumber.asGameVersion;

public record CoreMod(ModLoaderType modLoaderType, VersionRange<GameVersionNumber> mcVersionRange) {

    @NotNull
    @Unmodifiable
    public static List<CoreMod> fromFile(Path modFile, ZipFileTree tree) {
        if (!"jar".equalsIgnoreCase(FileUtils.getExtension(modFile))) return List.of();
        GameVersionNumber forgeMin = null, forgeMax = null, neoMin = null, neoMax = null;
        {
            // Below 1.13
            ZipArchiveEntry mf = tree.getEntry("META-INF/MANIFEST.MF");
            if (mf != null) {
                Attributes attr = null;
                try {
                    attr = new Manifest(tree.getInputStream(mf)).getMainAttributes();
                } catch (IOException e) {
                    LOG.warning("Failed to load jar manifest for jar: " + modFile, e);
                }
                if (attr != null) {
                    String fmlCorePlg = Objects.toString(attr.get("FMLCorePlugin"), null);
                    String tweakClass = Objects.toString(attr.get("TweakClass"), null);
                    if (StringUtils.isNotBlank(fmlCorePlg) || StringUtils.isNotBlank(tweakClass)) {
                        forgeMin = asGameVersion("1.6.1");
                        forgeMax = asGameVersion("1.12.2");
                    }
                }
            }
        }
        {
            // coremods.json in Forge 1.13-1.20.1 & NeoForge 1.21.4-
            // TODO Find a sample
            if (tree.getEntry("META-INF/coremods.json") != null) {
                forgeMin = Lang.minNullable(forgeMin, asGameVersion("1.13"));
                forgeMax = asGameVersion("1.21.10");
                neoMin = asGameVersion("1.20.1");
                neoMax = asGameVersion("1.21.4");
            }
        }
        {
            // ITransformationService
            if (tree.getEntry("META-INF/services/cpw.mods.modlauncher.api.ITransformationService") != null) {
                forgeMin = Lang.minNullable(forgeMin, asGameVersion("1.13"));
                forgeMax = GameVersionNumber.unknown();
                neoMin = asGameVersion("1.20.1");
                neoMax = asGameVersion("1.21.8");
            }
        }
        {
            // ICoreMod for NeoForge
            // TODO Find a sample
            if (tree.getEntry("META-INF/services/net.neoforged.neoforgespi.coremod.ICoreMod") != null) {
                neoMin = Lang.minNullable(neoMin, asGameVersion("1.20.5"));
                neoMax = GameVersionNumber.unknown();
            }
        }
        List<CoreMod> infoList = new ArrayList<>();
        if (forgeMin != null && forgeMax != null)
            infoList.add(new CoreMod(
                    ModLoaderType.FORGE,
                    forgeMax == GameVersionNumber.unknown() ? VersionRange.atLeast(forgeMin) : VersionRange.between(forgeMin, forgeMax)
            ));
        if (neoMin != null && neoMax != null)
            infoList.add(new CoreMod(
                    ModLoaderType.NEO_FORGE,
                    neoMax == GameVersionNumber.unknown() ? VersionRange.atLeast(neoMin) : VersionRange.between(neoMin, neoMax)
            ));
        return List.copyOf(infoList);
    }

    @NotNull
    @Unmodifiable
    public static Set<ModLoaderType> getSupportedLoaders(List<CoreMod> coreMods, @Nullable String gameVersion) {
        Set<ModLoaderType> supportedLoaders = EnumSet.noneOf(ModLoaderType.class);
        var version = GameVersionNumber.asGameVersion(Optional.ofNullable(gameVersion));
        for (var coreMod : coreMods)
            if (coreMod.mcVersionRange().contains(Objects.requireNonNullElse(GameVersionNumber.getReleaseOfSnapshot(version), version)))
                supportedLoaders.add(coreMod.modLoaderType());
        return Set.copyOf(supportedLoaders);
    }

}
