package org.jackhuang.hmcl.download.neoforge;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.VersionMismatchException;
import org.jackhuang.hmcl.download.forge.ForgeInstallTask;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class NeoForgedInstallTask extends Task<Version> {
    private final DefaultDependencyManager dependencyManager;

    private final Version version;

    private final NeoForgedRemoteVersion remoteVersion;

    private Path installer = null;

    private FileDownloadTask dependent;

    private Task<Version> dependency;

    public NeoForgedInstallTask(DefaultDependencyManager dependencyManager, Version version, NeoForgedRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.remoteVersion = remoteVersion;
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        installer = Files.createTempFile("neoforged-installer", ".jar");

        dependent = new FileDownloadTask(
                dependencyManager.getDownloadProvider().injectURLsWithCandidates(remoteVersion.getUrls()),
                installer.toFile(), null
        );
        dependent.setCacheRepository(dependencyManager.getCacheRepository());
        dependent.setCaching(true);
        dependent.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        Files.deleteIfExists(installer);
        this.setResult(dependency.getResult());
    }

    @Override
    public Collection<? extends Task<?>> getDependents() {
        return Collections.singleton(dependent);
    }

    @Override
    public Collection<? extends Task<?>> getDependencies() {
        return Collections.singleton(dependency);
    }

    @Override
    public void execute() throws Exception {
        dependency = install(dependencyManager, version, installer);
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
