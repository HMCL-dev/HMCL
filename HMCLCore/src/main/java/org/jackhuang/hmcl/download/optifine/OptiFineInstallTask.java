/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.download.optifine;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.game.Argument;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.LibrariesDownloadInfo;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.LibraryDownloadInfo;
import org.jackhuang.hmcl.game.StringArgument;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskResult;
import org.jackhuang.hmcl.util.Lang;

/**
 * <b>Note</b>: OptiFine should be installed in the end.
 *
 * @author huangyuhui
 */
public final class OptiFineInstallTask extends TaskResult<Version> {

    private final DefaultDependencyManager dependencyManager;
    private final String gameVersion;
    private final Version version;
    private final String remoteVersion;
    private final VersionList<?> optiFineVersionList;
    private RemoteVersion<?> remote;
    private final List<Task> dependents = new LinkedList<>();
    private final List<Task> dependencies = new LinkedList<>();

    private void doRemote() {
        remote = optiFineVersionList.getVersion(gameVersion, remoteVersion)
                .orElseThrow(() -> new IllegalArgumentException("Remote OptiFine version " + gameVersion + ", " + remoteVersion + " not found"));
    }

    public OptiFineInstallTask(DefaultDependencyManager dependencyManager, String gameVersion, Version version, String remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.gameVersion = gameVersion;
        this.version = version;
        this.remoteVersion = remoteVersion;

        optiFineVersionList = dependencyManager.getVersionList("optifine");

        if (!optiFineVersionList.isLoaded())
            dependents.add(optiFineVersionList.refreshAsync(dependencyManager.getDownloadProvider())
                    .then(s -> {
                        doRemote();
                        return null;
                    }));
        else
            doRemote();
    }

    @Override
    public Collection<Task> getDependents() {
        return dependents;
    }

    @Override
    public Collection<Task> getDependencies() {
        return dependencies;
    }

    @Override
    public String getId() {
        return "version";
    }

    @Override
    public boolean isRelyingOnDependencies() {
        return false;
    }

    @Override
    public void execute() throws Exception {
        Library library = new Library(
                "net.optifine", "optifine", remoteVersion, null, null,
                new LibrariesDownloadInfo(new LibraryDownloadInfo(
                        "net/optifine/optifine/" + remoteVersion + "/optifine-" + remoteVersion + ".jar",
                        remote.getUrl())), true
        );

        List<Library> libraries = new LinkedList<>();
        libraries.add(library);

        boolean hasFMLTweaker = false;
        if (version.getMinecraftArguments().isPresent() && version.getMinecraftArguments().get().contains("FMLTweaker"))
            hasFMLTweaker = true;
        if (version.getArguments().isPresent()) {
            List<Argument> game = version.getArguments().get().getGame();
            if (game.stream().anyMatch(arg -> arg.toString(Collections.EMPTY_MAP, Collections.EMPTY_MAP).contains("FMLTweaker")))
                hasFMLTweaker = true;
        }

        /*Arguments arguments = Lang.get(version.getArguments());

        if (!hasFMLTweaker)
            arguments = Arguments.addGameArguments(arguments, "--tweakClass", "optifine.OptiFineTweaker");
            */
        String minecraftArguments = version.getMinecraftArguments().orElse("");
        if (!hasFMLTweaker)
            minecraftArguments = minecraftArguments + " --tweakClass optifine.OptiFineTweaker";

        if (version.getMainClass() == null || !version.getMainClass().startsWith("net.minecraft.launchwrapper."))
            libraries.add(0, new Library("net.minecraft", "launchwrapper", "1.12"));

        setResult(version
                        .setLibraries(Lang.merge(version.getLibraries(), libraries))
                        .setMainClass("net.minecraft.launchwrapper.Launch")
                        .setMinecraftArguments(minecraftArguments)
                //.setArguments(arguments)
        );

        dependencies.add(new GameLibrariesTask(dependencyManager, version.setLibraries(libraries)));
    }

}
