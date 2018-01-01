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
package org.jackhuang.hmcl.download.game;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.game.AssetIndexInfo;
import org.jackhuang.hmcl.game.AssetObject;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.NetworkUtils;

/**
 *
 * @author huangyuhui
 */
public final class GameAssetDownloadTask extends Task {
    
    private final AbstractDependencyManager dependencyManager;
    private final Version version;
    private final GameAssetRefreshTask refreshTask;
    private final List<Task> dependents = new LinkedList<>();
    private final List<Task> dependencies = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides proxy settings and {@link org.jackhuang.hmcl.game.GameRepository}
     * @param version the <b>resolved</b> version
     */
    public GameAssetDownloadTask(AbstractDependencyManager dependencyManager, Version version) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.refreshTask = new GameAssetRefreshTask(dependencyManager, version);
        this.dependents.add(refreshTask);
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
    public void execute() throws Exception {
        int size = refreshTask.getResult().size();
        for (Map.Entry<File, AssetObject> entry : refreshTask.getResult()) {
            File file = entry.getKey();
            AssetObject assetObject = entry.getValue();
            String url = dependencyManager.getDownloadProvider().getAssetBaseURL() + assetObject.getLocation();
            if (!FileUtils.makeDirectory(file.getAbsoluteFile().getParentFile())) {
                Logging.LOG.log(Level.SEVERE, "Unable to create new file {0}, because parent directory cannot be created", file);
                continue;
            }
            if (file.isDirectory())
                continue;
            boolean flag = true;
            int downloaded = 0;
            try {
                // check the checksum of file to ensure that the file is not need to re-download.
                if (file.exists()) {
                    String sha1 = DigestUtils.sha1Hex(FileUtils.readBytes(file));
                    if (sha1.equals(assetObject.getHash())) {
                        ++downloaded;
                        Logging.LOG.finest("File $file has been downloaded successfully, skipped downloading");
                        updateProgress(downloaded, size);
                        continue;
                    }
                }
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Unable to get hash code of file " + file, e);
                flag = !file.exists();
            }
            if (flag) {
                FileDownloadTask task = new FileDownloadTask(NetworkUtils.toURL(url), file, dependencyManager.getProxy(), assetObject.getHash());
                task.setName(assetObject.getHash());
                dependencies.add(task);
            }
        }
    }
    
}
