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
package org.jackhuang.hmcl.download.game;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.game.AssetIndex;
import org.jackhuang.hmcl.game.AssetIndexInfo;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

/**
 * This task is to download asset index file provided in minecraft.json.
 *
 * @author huangyuhui
 */
public final class GameAssetIndexDownloadTask extends Task<Void> {

    private final AbstractDependencyManager dependencyManager;
    private final Version version;
    private final boolean forceDownloading;
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides {@link org.jackhuang.hmcl.game.GameRepository}
     * @param version the <b>resolved</b> version
     */
    public GameAssetIndexDownloadTask(AbstractDependencyManager dependencyManager, Version version, boolean forceDownloading) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.forceDownloading = forceDownloading;
        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() {
        AssetIndexInfo assetIndexInfo = version.getAssetIndex();
        Path assetIndexFile = dependencyManager.getGameRepository().getIndexFile(version.getId(), assetIndexInfo.getId());
        boolean verifyHashCode = StringUtils.isNotBlank(assetIndexInfo.getSha1()) && assetIndexInfo.getUrl().contains(assetIndexInfo.getSha1());

        if (Files.exists(assetIndexFile) && !forceDownloading) {
            // verify correctness of file content
            if (verifyHashCode) {
                try {
                    String actualSum = DigestUtils.digestToString("SHA-1", assetIndexFile);
                    if (actualSum.equalsIgnoreCase(assetIndexInfo.getSha1()))
                        return;
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to calculate sha1sum of file " + assetIndexInfo, e);
                    // continue downloading
                }
            } else {
                try {
                    JsonUtils.fromNonNullJson(FileUtils.readText(assetIndexFile), AssetIndex.class);
                    return;
                } catch (IOException | JsonParseException ignore) {
                }
            }
        }

        // We should not check the hash code of asset index file since this file is not consistent
        // And Mojang will modify this file anytime. So assetIndex.hash might be outdated.
        FileDownloadTask task = new FileDownloadTask(
                dependencyManager.getDownloadProvider().injectURLWithCandidates(assetIndexInfo.getUrl()),
                assetIndexFile.toFile(),
                verifyHashCode ? new FileDownloadTask.IntegrityCheck("SHA-1", assetIndexInfo.getSha1()) : null
        );
        task.setCacheRepository(dependencyManager.getCacheRepository());
        dependencies.add(task);
    }

    public static class GameAssetIndexMalformedException extends IOException {
    }
}
