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
package org.jackhuang.hmcl.modpack.modrinth;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.modpack.ModAdviser;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackExportInfo;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Exports one registered game instance as a Modrinth modpack archive.
@NotNullByDefault
public class ModrinthModpackExportTask extends Task<Void> {
    /// The fixed instance snapshot exported by this task.
    private final DefaultGameInstance instance;

    /// The mod manager associated with the exported instance.
    private final ModManager modManager;

    /// The validated export configuration.
    private final ModpackExportInfo info;

    /// The archive written by this task.
    private final Path modpackFile;

    /// Creates a Modrinth modpack export task.
    ///
    /// @param instance    the registered instance snapshot to export
    /// @param info        the export configuration
    /// @param modpackFile the archive to write
    public ModrinthModpackExportTask(DefaultGameInstance instance, ModpackExportInfo info, Path modpackFile) {
        this.instance = instance;
        this.modManager = instance.getModManager();
        this.info = info.validate();
        this.modpackFile = modpackFile;

        onDone().register(event -> {
            if (event.isFailed()) {
                try {
                    Files.deleteIfExists(modpackFile);
                } catch (IOException e) {
                    LOG.warning("Failed to delete modpack file: " + modpackFile, e);
                }
            }
        });
    }

    /// Returns a remote-file manifest entry for a local file when one can be identified.
    ///
    /// @param file         the local file
    /// @param relativePath the archive-relative path
    /// @return the remote-file entry, or `null` when the file must be included in overrides
    private @Nullable ModrinthManifest.File tryGetRemoteFile(Path file, String relativePath) throws IOException {
        if (info.isNoCreateRemoteFiles()) {
            return null;
        }

        boolean isDisabled = modManager.isDisabled(file);
        if (isDisabled) {
            relativePath = StringUtils.removeSuffix(relativePath, LocalAddonManager.DISABLED_EXTENSION);
        }

        Optional<RemoteAddon.Version> modrinthVersion = Optional.empty();
        Optional<RemoteAddon.Version> curseForgeVersion = Optional.empty();

        try {
            modrinthVersion = ModrinthRemoteAddonRepository.MODS.getRemoteVersionByLocalFile(file);
        } catch (IOException e) {
            LOG.warning("Failed to get remote file from Modrinth for: " + file, e);
        }

        if (!info.isSkipCurseForgeRemoteFiles() && CurseForgeRemoteAddonRepository.isAvailable()) {
            try {
                curseForgeVersion = CurseForgeRemoteAddonRepository.MODS.getRemoteVersionByLocalFile(file);
            } catch (IOException e) {
                LOG.warning("Failed to get remote file from CurseForge for: " + file, e);
            }
        }

        if (modrinthVersion.isEmpty() && curseForgeVersion.isEmpty()) {
            return null;
        }

        Map<String, String> hashes = new HashMap<>();
        hashes.put("sha1", DigestUtils.digestToString("SHA-1", file));
        hashes.put("sha512", DigestUtils.digestToString("SHA-512", file));

        @Nullable Map<String, String> env = null;
        if (isDisabled) {
            env = new HashMap<>();
            env.put("client", "optional");
        }

        List<String> downloads = new ArrayList<>();
        if (modrinthVersion.isPresent())
            downloads.add(modrinthVersion.get().file().url());
        if (curseForgeVersion.isPresent())
            downloads.add(curseForgeVersion.get().file().url());

        long fileSize = Files.size(file);
        if (fileSize > Integer.MAX_VALUE) {
            LOG.warning("File " + relativePath + " is too large (size: " + fileSize + " bytes), precision may be lost when converting to int");
        }
        return new ModrinthManifest.File(
                relativePath,
                hashes,
                env,
                downloads,
                (int) fileSize
        );
    }

    /// {@inheritDoc}
    @Override
    public void execute() throws Exception {
        var instanceId = instance.getId();
        ArrayList<String> blackList = new ArrayList<>(ModAdviser.MODPACK_BLACK_LIST);
        blackList.add(instanceId + ".jar");
        blackList.add(instanceId + ".json");
        LOG.info("Compressing game files without some files in blacklist, including files or directories: usernamecache.json, asm, logs, backups, versions, assets, usercache.json, libraries, crash-reports, launcher_profiles.json, NVIDIA, TCNodeTracker");
        try (var zip = new Zipper(modpackFile)) {
            Path runDirectory = instance.getRunDirectory();
            List<ModrinthManifest.File> files = new ArrayList<>();
            Set<String> filesInManifest = new HashSet<>();

            String[] resourceDirs = {"resourcepacks", "shaderpacks", "mods"};
            for (String dir : resourceDirs) {
                Path dirPath = runDirectory.resolve(dir);
                if (Files.exists(dirPath)) {
                    Files.walk(dirPath)
                            .filter(Files::isRegularFile)
                            .forEach(file -> {
                                try {
                                    String relativePath = runDirectory.relativize(file).normalize().toString().replace(File.separatorChar, '/');

                                    if (!info.getWhitelist().contains(relativePath)) {
                                        return;
                                    }

                                    ModrinthManifest.File fileEntry = tryGetRemoteFile(file, relativePath);
                                    if (fileEntry != null) {
                                        files.add(fileEntry);
                                        filesInManifest.add(relativePath);
                                    }
                                } catch (IOException e) {
                                    LOG.warning("Failed to process file: " + file, e);
                                }
                            });
                }
            }

            zip.putDirectory(runDirectory, "client-overrides", path -> {
                String relativePath = path.replace(File.separatorChar, '/');
                if (filesInManifest.contains(relativePath)) {
                    return false;
                }
                return Modpack.acceptFile(path, blackList, info.getWhitelist());
            });

            GameVersionNumber version = instance.getVersion();
            if (version == GameVersionNumber.unknown()) {
                throw new IOException("Cannot parse the version of " + instanceId);
            }
            String gameVersion = version.toString();
            GameComponentAnalyzer analyzer = instance.getAnalyzer();

            Map<String, String> dependencies = new HashMap<>();
            dependencies.put("minecraft", gameVersion);

            Optional.ofNullable(analyzer.getVersion(GameComponentType.FORGE)).ifPresent(forgeVersion ->
                    dependencies.put("forge", forgeVersion));
            Optional.ofNullable(analyzer.getVersion(GameComponentType.NEO_FORGE)).ifPresent(neoForgeVersion ->
                    dependencies.put("neoforge", neoForgeVersion));
            Optional.ofNullable(analyzer.getVersion(GameComponentType.FABRIC)).ifPresent(fabricVersion ->
                    dependencies.put("fabric-loader", fabricVersion));
            Optional.ofNullable(analyzer.getVersion(GameComponentType.QUILT)).ifPresent(quiltVersion ->
                    dependencies.put("quilt-loader", quiltVersion));

            ModrinthManifest manifest = new ModrinthManifest(
                    "minecraft",
                    1,
                    info.getVersion(),
                    info.getName(),
                    info.getDescription(),
                    files,
                    dependencies
            );

            zip.putTextFile(JsonUtils.GSON.toJson(manifest), "modrinth.index.json");
        }
    }

    /// Export options supported by the Modrinth format.
    public static final ModpackExportInfo.Options OPTION = new ModpackExportInfo.Options()
            .requireNoCreateRemoteFiles()
            .requireSkipCurseForgeRemoteFiles();
}
