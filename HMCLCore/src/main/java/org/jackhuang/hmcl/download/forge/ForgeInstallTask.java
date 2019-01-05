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
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.SimpleVersionProvider;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskResult;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.*;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 *
 * @author huangyuhui
 */
public final class ForgeInstallTask extends TaskResult<Version> {

    private final DefaultDependencyManager dependencyManager;
    private final Version version;
    private final File installer = new File("forge-installer.jar").getAbsoluteFile();
    private final ForgeRemoteVersion remote;
    private final List<Task> dependents = new LinkedList<>();
    private final List<Task> dependencies = new LinkedList<>();

    private Task downloadFileTask() {
        return new FileDownloadTask(NetworkUtils.toURL(remote.getUrl()), installer);
    }

    public ForgeInstallTask(DefaultDependencyManager dependencyManager, Version version, ForgeRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.remote = remoteVersion;

        dependents.add(downloadFileTask());
    }

    @Override
    public Collection<Task> getDependents() {
        return dependents;
    }

    @Override
    public List<Task> getDependencies() {
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
        try (ZipFile zipFile = new ZipFile(installer)) {
            InputStream stream = zipFile.getInputStream(zipFile.getEntry("install_profile.json"));
            if (stream == null)
                throw new IOException("Malformed forge installer file, install_profile.json does not exist.");
            String json = IOUtils.readFullyAsString(stream);
            ForgeInstallProfile installProfile = JsonUtils.fromNonNullJson(json, ForgeInstallProfile.class);
            
            // unpack the universal jar in the installer file.
            Library forgeLibrary = Library.fromName(installProfile.getInstall().getPath());
            File forgeFile = dependencyManager.getGameRepository().getLibraryFile(version, forgeLibrary);
            if (!FileUtils.makeFile(forgeFile))
                throw new IOException("Cannot make directory " + forgeFile.getParent());
            
            ZipEntry forgeEntry = zipFile.getEntry(installProfile.getInstall().getFilePath());
            try (InputStream is = zipFile.getInputStream(forgeEntry); OutputStream os = new FileOutputStream(forgeFile)) {
                IOUtils.copyTo(is, os);
            }
            
            // resolve the version
            SimpleVersionProvider provider = new SimpleVersionProvider();
            provider.addVersion(version);
            
            setResult(installProfile.getVersionInfo()
                    .setInheritsFrom(version.getId())
                    .resolve(provider).setJar(null)
                    .setId(version.getId()).setLogging(Collections.emptyMap()));
            
            dependencies.add(dependencyManager.checkLibraryCompletionAsync(installProfile.getVersionInfo()));
        }
        
        if (!installer.delete())
            throw new IOException("Unable to delete installer file" + installer);
    }
}
