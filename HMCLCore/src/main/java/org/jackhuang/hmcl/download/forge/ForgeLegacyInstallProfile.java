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
package org.jackhuang.hmcl.download.forge;

import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.addon.meta.ForgeOldModMetadata;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public record ForgeLegacyInstallProfile(@Nullable String gameVersion, @Nullable String forgeVersion) {
    @Nullable
    public static ForgeLegacyInstallProfile parse(Path forgeArchive) {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(forgeArchive)) {
            String gameVersion = null;
            String forgeVersion = null;

            if (Files.isRegularFile(fs.getPath("fmlversion.properties"))) {
                Properties properties = new Properties();
                properties.load(Files.newInputStream(fs.getPath("fmlversion.properties")));

                gameVersion = properties.getProperty("fmlbuild.mcversion");
            }

            if (Files.isRegularFile(fs.getPath("forgeversion.properties"))) {
                Properties properties = new Properties();
                properties.load(Files.newInputStream(fs.getPath("forgeversion.properties")));
                List<String> list = new ArrayList<>();
                list.add(properties.getProperty("forge.major.number"));
                list.add(properties.getProperty("forge.minor.number"));
                list.add(properties.getProperty("forge.revision.number"));
                list.add(properties.getProperty("forge.build.number"));
                forgeVersion = String.join(".", list);
            }

            if (forgeVersion == null && Files.isRegularFile(fs.getPath("mod_MinecraftForge.info"))) {
                try (ZipFileTree tree = new ZipFileTree(new ZipArchiveReader(forgeArchive))) {
                    LocalModFile metadata = ForgeOldModMetadata.fromFile(null, forgeArchive, tree, "mod_MinecraftForge.info");
                    forgeVersion = metadata.getVersion();
                }
            }

            if (forgeVersion == null && gameVersion == null && !Files.isRegularFile(fs.getPath("mod_MinecraftForge.class"))) {
                return null;
            }

            return new ForgeLegacyInstallProfile(gameVersion, forgeVersion);
        } catch (IOException ioException) {
            LOG.warning("Failed to parse forge archive " + forgeArchive, ioException);
            return null;
        }
    }
}
