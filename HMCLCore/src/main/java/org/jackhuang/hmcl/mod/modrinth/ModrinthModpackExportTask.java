package org.jackhuang.hmcl.mod.modrinth;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.mod.ModAdviser;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackExportInfo;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.*;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ModrinthModpackExportTask extends Task<Void> {
    private final DefaultGameRepository repository;
    private final String version;
    private final ModpackExportInfo info;
    private final Path modpackFile;

    public ModrinthModpackExportTask(DefaultGameRepository repository, String version, ModpackExportInfo info, Path modpackFile) {
        this.repository = repository;
        this.version = version;
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

    private ModrinthManifest.File tryGetRemoteFile(Path file, String relativePath) throws IOException {
        if (info.isNoCreateRemoteFiles()) {
            return null;
        }

        boolean isDisabled = repository.getModManager(version).isDisabled(file);
        if (isDisabled) {
            relativePath = repository.getModManager(version).enableMod(Paths.get(relativePath)).toString();
        }

        LocalModFile localModFile = null;
        Optional<RemoteMod.Version> modrinthVersion = Optional.empty();
        Optional<RemoteMod.Version> curseForgeVersion = Optional.empty();

        try {
            modrinthVersion = ModrinthRemoteModRepository.MODS.getRemoteVersionByLocalFile(localModFile, file);
        } catch (IOException e) {
            LOG.warning("Failed to get remote file from Modrinth for: " + file, e);
        }

        if (!info.isSkipCurseForgeRemoteFiles() && CurseForgeRemoteModRepository.isAvailable()) {
            try {
                curseForgeVersion = CurseForgeRemoteModRepository.MODS.getRemoteVersionByLocalFile(localModFile, file);
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

        Map<String, String> env = null;
        if (isDisabled) {
            env = new HashMap<>();
            env.put("client", "optional");
        }

        List<String> downloads = new ArrayList<>();
        if (modrinthVersion.isPresent())
            downloads.add(modrinthVersion.get().getFile().getUrl());
        if (curseForgeVersion.isPresent())
            downloads.add(curseForgeVersion.get().getFile().getUrl());

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

    @Override
    public void execute() throws Exception {
        ArrayList<String> blackList = new ArrayList<>(ModAdviser.MODPACK_BLACK_LIST);
        blackList.add(version + ".jar");
        blackList.add(version + ".json");
        LOG.info("Compressing game files without some files in blacklist, including files or directories: usernamecache.json, asm, logs, backups, versions, assets, usercache.json, libraries, crash-reports, launcher_profiles.json, NVIDIA, TCNodeTracker");
        try (var zip = new Zipper(modpackFile)) {
            Path runDirectory = repository.getRunDirectory(version);
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

            String gameVersion = repository.getGameVersion(version)
                    .orElseThrow(() -> new IOException("Cannot parse the version of " + version));
            LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(repository.getResolvedPreservingPatchesVersion(version), gameVersion);

            Map<String, String> dependencies = new HashMap<>();
            dependencies.put("minecraft", gameVersion);

            analyzer.getVersion(FORGE).ifPresent(forgeVersion ->
                    dependencies.put("forge", forgeVersion));
            analyzer.getVersion(NEO_FORGE).ifPresent(neoForgeVersion ->
                    dependencies.put("neoforge", neoForgeVersion));
            analyzer.getVersion(FABRIC).ifPresent(fabricVersion ->
                    dependencies.put("fabric-loader", fabricVersion));
            analyzer.getVersion(QUILT).ifPresent(quiltVersion ->
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

    public static final ModpackExportInfo.Options OPTION = new ModpackExportInfo.Options()
            .requireNoCreateRemoteFiles()
            .requireSkipCurseForgeRemoteFiles();
}
