// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.game;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskProgress;
import org.jackhuang.hmcl.task.TaskStage;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Downloads the assets listed by a game manifest's asset index.
///
/// @author huangyuhui
public final class GameAssetDownloadTask extends Task<@Nullable Void> {

    /// Supplies the asset layout, download sources and cache.
    private final AbstractDependencyManager dependencyManager;
    /// Identifies the asset index to install.
    private final GameInstanceManifest manifest;
    /// Whether to download the index even when a valid local copy exists.
    private final boolean forceDownloadingIndex;
    /// Whether to verify existing asset files against their recorded hashes.
    private final boolean integrityCheck;

    /// Creates a task that installs the asset index and its referenced assets.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param manifest the game manifest that declares the asset index
    /// @param forceDownloadingIndex whether to download the index even if the local copy is valid
    /// @param integrityCheck whether to verify the hashes of existing asset files
    public GameAssetDownloadTask(AbstractDependencyManager dependencyManager, GameInstanceManifest manifest, boolean forceDownloadingIndex, boolean integrityCheck) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.forceDownloadingIndex = forceDownloadingIndex;
        this.integrityCheck = integrityCheck;
    }

    /// Returns the stage for game asset downloads.
    @Override
    public TaskStage getStage() {
        return InstallStages.ASSETS;
    }

    /// Obtains the asset index, verifies existing assets and downloads the missing or invalid files in parallel.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        context.run(new GameAssetIndexDownloadTask(dependencyManager, manifest, forceDownloadingIndex));
        GameRepositoryLayout layout = dependencyManager.getGameRepository().getLayout();
        Path assetIndexFile = layout.getAssetIndexFile(manifest.getAssetIndex().getId());
        List<Task<?>> dependencies = new ArrayList<>();
        AssetIndex index;
        try {
            index = JsonUtils.fromNonNullJson(Files.readString(assetIndexFile), AssetIndex.class);
        } catch (IOException | JsonParseException e) {
            throw new GameAssetIndexDownloadTask.GameAssetIndexMalformedException();
        }

        TaskProgress progress = context.progress();
        int completed = 0;
        for (AssetObject assetObject : index.getObjects().values()) {
            if (context.isCancelled())
                throw new InterruptedException();

            Path file = layout.getAssetObject(assetObject);
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

            progress.update(++completed, index.getObjects().size());
        }

        if (!dependencies.isEmpty()) {
            context.addStageItems(dependencies.size());
        }
        context.runAll(dependencies, FetchTask.getDownloadExecutorConcurrency());
        return null;
    }

    /// Downloads the index even when the local copy is valid.
    public static final boolean DOWNLOAD_INDEX_FORCIBLY = true;
    /// Reuses a valid local index.
    public static final boolean DOWNLOAD_INDEX_IF_NECESSARY = false;
}
