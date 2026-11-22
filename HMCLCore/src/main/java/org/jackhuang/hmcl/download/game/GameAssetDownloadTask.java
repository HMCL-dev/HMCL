// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.game;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.StageId;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 *
 * @author huangyuhui
 */
public final class GameAssetDownloadTask extends Task<@Nullable Void> {

    private final AbstractDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private final AssetIndexInfo assetIndexInfo;
    private final Path assetIndexFile;
    private final boolean integrityCheck;
    /// The index that must be available before asset files can be enumerated.
    private final GameAssetIndexDownloadTask indexTask;

    /// Constructor.
    ///
    /// @param dependencyManager the dependency manager that can provides [GameRepository]
    /// @param manifest the game version
    public GameAssetDownloadTask(AbstractDependencyManager dependencyManager, GameInstanceManifest manifest, boolean forceDownloadingIndex, boolean integrityCheck) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.assetIndexInfo = this.manifest.getAssetIndex();
        GameRepository gameRepository = dependencyManager.getGameRepository();
        String assetId = assetIndexInfo.getId();
        this.assetIndexFile = gameRepository.getLayout().getAssetIndexFile(assetId);
        this.integrityCheck = integrityCheck;

        indexTask = new GameAssetIndexDownloadTask(dependencyManager, this.manifest, forceDownloadingIndex);
    }

    /// Returns the stage for game asset downloads.
    @Override
    public StageId getStage() {
        return InstallStages.ASSETS;
    }

    /// Obtains the asset index, verifies existing assets and downloads the missing or invalid files in parallel.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        context.run(indexTask);
        List<Task<?>> dependencies = new ArrayList<>();
        AssetIndex index;
        try {
            index = JsonUtils.fromNonNullJson(Files.readString(assetIndexFile), AssetIndex.class);
        } catch (IOException | JsonParseException e) {
            throw new GameAssetIndexDownloadTask.GameAssetIndexMalformedException();
        }

        int progress = 0;
        for (AssetObject assetObject : index.getObjects().values()) {
            if (context.isCancelled())
                throw new InterruptedException();

            GameRepository gameRepository = dependencyManager.getGameRepository();
            Path file = gameRepository.getLayout().getAssetObject(assetObject);
            boolean download = !Files.isRegularFile(file);
            try {
                if (!download && integrityCheck && !assetObject.validateChecksum(file, true))
                    download = true;
            } catch (IOException e) {
                LOG.warning("Unable to calc hash value of file " + file, e);
            }
            if (download) {
                DownloadCandidates candidates = dependencyManager.getDownloadProvider().getAssetObjectCandidates(assetObject);
                var task = new FileDownloadTask(candidates, file, new FileDownloadTask.IntegrityCheck("SHA-1", assetObject.hash()));
                task.setName(assetObject.hash());
                task.setCandidate(dependencyManager.getCacheRepository().getCommonDirectory()
                        .resolve("assets").resolve("objects").resolve(assetObject.getLocation()));
                task.setCacheRepository(dependencyManager.getCacheRepository());
                task.setCaching(true);
                dependencies.add(task.withCounter());
            } else {
                dependencyManager.getCacheRepository().tryCacheFile(file, CacheRepository.SHA1, assetObject.hash());
            }

            updateProgress(++progress, index.getObjects().size());
        }

        if (!dependencies.isEmpty()) {
            context.addStageItems(dependencies.size());
        }
        context.runAll(dependencies);
        return null;
    }

    public static final boolean DOWNLOAD_INDEX_FORCIBLY = true;
    public static final boolean DOWNLOAD_INDEX_IF_NECESSARY = false;
}
