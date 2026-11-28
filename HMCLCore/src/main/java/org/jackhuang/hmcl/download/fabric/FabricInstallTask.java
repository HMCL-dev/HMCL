// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.UnsupportedInstallationException;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

import static org.jackhuang.hmcl.download.UnsupportedInstallationException.FABRIC_NOT_COMPATIBLE_WITH_FORGE;

/**
 * <b>Note</b>: Fabric should be installed first.
 *
 * @author huangyuhui
 */
public final class FabricInstallTask extends Task<GameInstancePatch> {

    private final DefaultDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private final FabricRemoteVersion remote;

    /// Creates an installation task for the selected Fabric loader version.
    ///
    /// @throws IllegalArgumentException if `manifest` is not modifiable
    public FabricInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, FabricRemoteVersion remoteVersion) {
        if (!manifest.isModifiable()) {
            throw new IllegalArgumentException("Manifest is not modifiable");
        }

        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.remote = remoteVersion;
    }

    /// Reads the loader metadata and returns its patch after attempting to download the required libraries.
    @Override
    public GameInstancePatch execute(TaskExecutionContext context) throws Exception {
        if (!Objects.equals(GameComponentAnalyzer.VANILLA_MAIN, manifest.mainClass()))
            throw new UnsupportedInstallationException(FABRIC_NOT_COMPATIBLE_WITH_FORGE);

        GetTask launchMetaTask = new GetTask(dependencyManager.getDownloadProvider().getDownloadCandidates(remote));
        launchMetaTask.setCacheRepository(dependencyManager.getCacheRepository());
        @Nullable FabricInfo fabricInfo = JsonUtils.GSON.fromJson(context.run(launchMetaTask), FabricInfo.class);
        if (fabricInfo == null)
            throw new IOException("Fabric metadata is invalid");

        GameInstancePatch patch = getPatch(fabricInfo, remote.getGameVersion(), remote.getSelfVersion());

        context.tryRun(new GameLibrariesTask(dependencyManager, manifest, true, patch.getLibraries()));
        return patch;
    }

    private GameInstancePatch getPatch(FabricInfo fabricInfo, GameVersionNumber gameVersion, String loaderVersion) {
        JsonObject launcherMeta = fabricInfo.launcherMeta;
        Arguments arguments = new Arguments();

        String mainClass;
        if (!launcherMeta.get("mainClass").isJsonObject()) {
            mainClass = launcherMeta.get("mainClass").getAsString();
        } else {
            mainClass = launcherMeta.get("mainClass").getAsJsonObject().get("client").getAsString();
        }

        if (launcherMeta.has("launchwrapper")) {
            String clientTweaker = launcherMeta.get("launchwrapper").getAsJsonObject().get("tweakers").getAsJsonObject().get("client").getAsJsonArray().get(0).getAsString();
            arguments = arguments.addGameArguments("--tweakClass", clientTweaker);
        }

        JsonObject librariesObject = launcherMeta.getAsJsonObject("libraries");
        List<Library> libraries = new ArrayList<>();

        // "common, server" is hard coded in fabric installer.
        // Don't know the purpose of ignoring client libraries.
        for (String side : new String[]{"common", "server"}) {
            for (JsonElement element : librariesObject.getAsJsonArray(side)) {
                libraries.add(JsonUtils.GSON.fromJson(element, Library.class));
            }
        }

        libraries.add(new Library(Artifact.fromDescriptor(fabricInfo.intermediary.maven), "https://maven.fabricmc.net/", null));
        libraries.add(new Library(Artifact.fromDescriptor(fabricInfo.loader.maven), "https://maven.fabricmc.net/", null));

        return new GameInstancePatch(GameComponentType.FABRIC.getPatchId(), loaderVersion, GameInstancePatch.PRIORITY_LOADER, arguments, mainClass, libraries);
    }

    @JsonSerializable
    public static class FabricInfo {
        private final LoaderInfo loader;
        private final IntermediaryInfo intermediary;
        private final JsonObject launcherMeta;

        public FabricInfo(LoaderInfo loader, IntermediaryInfo intermediary, JsonObject launcherMeta) {
            this.loader = loader;
            this.intermediary = intermediary;
            this.launcherMeta = launcherMeta;
        }

        public LoaderInfo getLoader() {
            return loader;
        }

        public IntermediaryInfo getIntermediary() {
            return intermediary;
        }

        public JsonObject getLauncherMeta() {
            return launcherMeta;
        }
    }

    @JsonSerializable
    public static class LoaderInfo {
        private final String separator;
        private final int build;
        private final String maven;
        private final String version;
        private final boolean stable;

        public LoaderInfo(String separator, int build, String maven, String version, boolean stable) {
            this.separator = separator;
            this.build = build;
            this.maven = maven;
            this.version = version;
            this.stable = stable;
        }

        public String getSeparator() {
            return separator;
        }

        public int getBuild() {
            return build;
        }

        public String getMaven() {
            return maven;
        }

        public String getVersion() {
            return version;
        }

        public boolean isStable() {
            return stable;
        }
    }

    @JsonSerializable
    public static class IntermediaryInfo {
        private final String maven;
        private final String version;
        private final boolean stable;

        public IntermediaryInfo(String maven, String version, boolean stable) {
            this.maven = maven;
            this.version = version;
            this.stable = stable;
        }

        public String getMaven() {
            return maven;
        }

        public String getVersion() {
            return version;
        }

        public boolean isStable() {
            return stable;
        }
    }
}
