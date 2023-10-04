package org.jackhuang.hmcl.download.neoforge;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.VersionMismatchException;
import org.jackhuang.hmcl.download.forge.ForgeInstallTask;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class NeoForgedInstallTask {
    private NeoForgedInstallTask() {
    }

    public static Task<Version> install(DefaultDependencyManager dependencyManager, Version version, Path installer) throws IOException, VersionMismatchException {
        Optional<String> gameVersion = dependencyManager.getGameRepository().getGameVersion(version);
        if (!gameVersion.isPresent()) throw new IOException();
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            String installProfileText = FileUtils.readText(fs.getPath("install_profile.json"));
            Map<?, ?> installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (!installProfileText.toLowerCase(Locale.ROOT).contains("neoforge")) {
                throw new IOException();
            }
            Object p = installProfile.get("profile");
            if (!(p instanceof String)) {
                throw new IOException();
            }
            if (!p.equals(LibraryAnalyzer.LibraryType.FORGE.getPatchId())) {
                throw new IOException();
            }
        }

        return ForgeInstallTask.install(dependencyManager, version, installer).thenApplyAsync(neoForgeVersion -> {
            if (!neoForgeVersion.getId().equals(LibraryAnalyzer.LibraryType.FORGE.getPatchId()) || neoForgeVersion.getVersion() == null) {
                throw new IOException("Invalid neoforged version null.");
            }
            return neoForgeVersion.setId(LibraryAnalyzer.LibraryType.NEO_FORGED.getPatchId()).setVersion(neoForgeVersion.getVersion().replace(LibraryAnalyzer.LibraryType.FORGE.getPatchId(), LibraryAnalyzer.LibraryType.NEO_FORGED.getPatchId()));
        });
    }
}
