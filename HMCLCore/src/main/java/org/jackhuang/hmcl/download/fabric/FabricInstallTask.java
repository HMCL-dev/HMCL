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
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.util.*;

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

        launchMetaTask = new GetTask(NetworkUtils.toURL(dependencyManager.getPrimaryDownloadProvider().injectURL(getLaunchMetaUrl(remote.getGameVersion(), remote.getSelfVersion()))))
                .setCacheRepository(dependencyManager.getCacheRepository());
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        if (!Objects.equals("net.minecraft.client.main.Main", version.resolve(dependencyManager.getGameRepository()).getMainClass()))
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
    public void execute() {
        setResult(getPatch(JsonUtils.GSON.fromJson(launchMetaTask.getResult(), FabricInfo.class), remote.getGameVersion(), remote.getSelfVersion()));

        dependencies.add(dependencyManager.checkLibraryCompletionAsync(getResult(), true));
    }

    private static String getLaunchMetaUrl(String gameVersion, String loaderVersion) {
        return String.format("https://meta.fabricmc.net/v2/versions/loader/%s/%s", gameVersion, loaderVersion);
    }

    private Version getPatch(FabricInfo fabricInfo, String gameVersion, String loaderVersion) {
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

        return new Version(LibraryAnalyzer.LibraryType.FABRIC.getPatchId(), loaderVersion, 30000, arguments, mainClass, libraries);
    }

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

    public static class UnsupportedFabricInstallationException extends Exception {
    }
}
