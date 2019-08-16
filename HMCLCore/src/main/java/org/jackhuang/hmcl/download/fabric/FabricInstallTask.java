/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * <b>Note</b>: Fabric should be installed first.
 *
 * @author huangyuhui
 */
public final class FabricInstallTask extends Task<Version> {

    private final DefaultDependencyManager dependencyManager;
    private final Version version;
    private final FabricRemoteVersion remote;
    private final GetTask launchMetaTask;
    private final List<Task<?>> dependencies = new LinkedList<>();

    public FabricInstallTask(DefaultDependencyManager dependencyManager, Version version, FabricRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.remote = remoteVersion;

        launchMetaTask = new GetTask(NetworkUtils.toURL(getLaunchMetaUrl(remote.getSelfVersion())))
                .setCacheRepository(dependencyManager.getCacheRepository());
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() {
        if (!Objects.equals("net.minecraft.client.main.Main", version.getMainClass()))
            throw new UnsupportedFabricInstallationException();
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
        setResult(getPatch(JsonUtils.GSON.fromJson(launchMetaTask.getResult(), JsonObject.class), remote.getGameVersion(), remote.getSelfVersion()));

        dependencies.add(dependencyManager.checkLibraryCompletionAsync(getResult()));
    }

    private static String getLaunchMetaUrl(String loaderVersion) {
        return String.format("%s/%s/%s/%s/%3$s-%4$s.json", "https://maven.fabricmc.net/", "net/fabricmc", "fabric-loader", loaderVersion);
    }

    private Version getPatch(JsonObject jsonObject, String gameVersion, String loaderVersion) {
        Arguments arguments = new Arguments();

        String mainClass;
        if (!jsonObject.get("mainClass").isJsonObject()) {
            mainClass = jsonObject.get("mainClass").getAsString();
        } else {
            mainClass = jsonObject.get("mainClass").getAsJsonObject().get("client").getAsString();
        }

        if (jsonObject.has("launchwrapper")) {
            String clientTweaker = jsonObject.get("launchwrapper").getAsJsonObject().get("tweakers").getAsJsonObject().get("client").getAsJsonArray().get(0).getAsString();
            arguments = arguments.addGameArguments("--tweakClass", clientTweaker);
        }

        JsonObject librariesObject = jsonObject.getAsJsonObject("libraries");
        List<Library> libraries = new ArrayList<>();

        // "common, server" is hard coded in fabric installer.
        // Don't know the purpose of ignoring client libraries.
        for (String side : new String[]{"common", "server"}) {
            for (JsonElement element : librariesObject.getAsJsonArray(side)) {
                libraries.add(JsonUtils.GSON.fromJson(element, Library.class));
            }
        }

        libraries.add(new Library("net.fabricmc", "intermediary", gameVersion, null, "https://maven.fabricmc.net/", null));
        libraries.add(new Library("net.fabricmc", "fabric-loader", loaderVersion, null, "https://maven.fabricmc.net/", null));

        return new Version("net.fabricmc", loaderVersion, 30000, arguments, mainClass, libraries);
    }

    public static class UnsupportedFabricInstallationException extends UnsupportedOperationException {
    }
}
