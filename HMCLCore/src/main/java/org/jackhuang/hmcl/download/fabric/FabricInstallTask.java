/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.UnsupportedInstallationException;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.util.*;

import static org.jackhuang.hmcl.download.UnsupportedInstallationException.FABRIC_NOT_COMPATIBLE_WITH_FORGE;

/**
 * <b>Note</b>: Fabric should be installed first.
 *
 * @author huangyuhui
 */
public class FabricInstallTask extends Task<GameInstancePatch> {

    private final DefaultDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private final ComponentRemoteVersion remote;
    private final GetTask launchMetaTask;
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    public FabricInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, ComponentRemoteVersion remoteVersion) {
        if (!manifest.isModifiable()) {
            throw new IllegalArgumentException("Manifest is not modifiable");
        }

        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.remote = remoteVersion;

        launchMetaTask = new GetTask(dependencyManager.getDownloadProvider().injectURLsWithCandidates(remoteVersion.getUrls()));
        launchMetaTask.setCacheRepository(dependencyManager.getCacheRepository());
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        if (!Objects.equals(GameComponentAnalyzer.VANILLA_MAIN, manifest.mainClass()))
            throw new UnsupportedInstallationException(FABRIC_NOT_COMPATIBLE_WITH_FORGE);
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return Collections.singleton(launchMetaTask);
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isRelyingOnDependencies() {
        return false;
    }

    @Override
    public void execute() throws IOException {
        FabricInfo fabricInfo = JsonUtils.GSON.fromJson(launchMetaTask.getResult(), FabricInfo.class);
        if (fabricInfo == null) throw new IOException("Fabric metadata is invalid");

        setResult(getPatch(fabricInfo, remote.getGameVersion(), remote.getSelfVersion()));

        dependencies.add(new GameLibrariesTask(dependencyManager, manifest, true, getResult().getLibraries()));
    }

    protected GameInstancePatch getPatch(FabricInfo fabricInfo, String gameVersion, String loaderVersion) {
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

        libraries.add(new Library(Artifact.fromDescriptor(fabricInfo.intermediary.maven), getMavenRepositoryByGroup(fabricInfo.intermediary.maven), null));
        libraries.add(new Library(Artifact.fromDescriptor(fabricInfo.loader.maven), getMavenRepositoryByGroup(fabricInfo.loader.maven), null));

        return new GameInstancePatch(GameComponentType.FABRIC.getPatchId(), loaderVersion, GameInstancePatch.PRIORITY_LOADER, arguments, mainClass, libraries);
    }


    protected String getMavenRepositoryByGroup(String maven) {
        return "https://maven.fabricmc.net/";
    }

    @JsonSerializable
    public record FabricInfo(LoaderInfo loader, IntermediaryInfo intermediary, JsonObject launcherMeta) {
    }

    @JsonSerializable
    public record LoaderInfo(String separator, int build, String maven, String version, boolean stable) {
    }

    @JsonSerializable
    public record IntermediaryInfo(String maven, String version, boolean stable) {
    }
}
