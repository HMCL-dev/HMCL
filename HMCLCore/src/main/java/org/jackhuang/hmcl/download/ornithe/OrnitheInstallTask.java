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
package org.jackhuang.hmcl.download.ornithe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.UnsupportedInstallationException;
import org.jackhuang.hmcl.download.fabric.FabricInstallTask;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
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
public final class OrnitheInstallTask extends Task<Version> {

    private final DefaultDependencyManager dependencyManager;
    private final Version version;
    private final OrnitheRemoteVersion remote;
    private final GetTask launchMetaTask;
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    public OrnitheInstallTask(DefaultDependencyManager dependencyManager, Version version, OrnitheRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.version = version;
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
        if (!Objects.equals("net.minecraft.client.main.Main", version.resolve(dependencyManager.getGameRepository()).getMainClass()))
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
        OrnitheInfo ornitheInfo = JsonUtils.GSON.fromJson(launchMetaTask.getResult(), OrnitheInfo.class);
        if (ornitheInfo == null)
            throw new IOException("Fabric metadata is invalid");

        setResult(getPatch(ornitheInfo, remote.getGameVersion(), remote.getSelfVersion()));

        dependencies.add(dependencyManager.checkLibraryCompletionAsync(getResult(), true));
    }

    private Version getPatch(OrnitheInfo ornitheInfo, String gameVersion, String loaderVersion) {
        JsonObject launcherMeta = ornitheInfo.launcherMeta();
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

        libraries.add(new Library(Artifact.fromDescriptor(ornitheInfo.calamus().getMaven()), "https://maven.ornithemc.net/releases/", null));
        libraries.add(new Library(Artifact.fromDescriptor(ornitheInfo.loader().getMaven()), "https://maven.ornithemc.net/releases/", null));

        return new Version(LibraryAnalyzer.LibraryType.ORNITHE.getPatchId(), loaderVersion, Version.PRIORITY_LOADER, arguments, mainClass, libraries);
    }

    @JsonSerializable
    public record OrnitheInfo(FabricInstallTask.LoaderInfo loader, FabricInstallTask.IntermediaryInfo calamus, JsonObject launcherMeta) {
    }
}
