// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.game;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * This task is to download asset index file provided in minecraft.json.
 *
 * @author huangyuhui
 */
public final class GameAssetIndexDownloadTask extends Task<@Nullable Void> {

    private final AbstractDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private final boolean forceDownloading;

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides {@link GameRepository}
     * @param manifest the <b>resolved</b> version
     */
    public GameAssetIndexDownloadTask(AbstractDependencyManager dependencyManager, GameInstanceManifest manifest, boolean forceDownloading) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.forceDownloading = forceDownloading;
        setSignificance(TaskSignificance.MODERATE);
    }

    /// Validates the local asset index, downloading it when required.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        AssetIndexInfo assetIndexInfo = manifest.getAssetIndex();
        GameRepository gameRepository = dependencyManager.getGameRepository();
        String assetId = assetIndexInfo.getId();
        Path assetIndexFile = gameRepository.getLayout().getAssetIndexFile(assetId);
        boolean verifyHashCode = StringUtils.isNotBlank(assetIndexInfo.getSha1()) && assetIndexInfo.getUrl().contains(assetIndexInfo.getSha1());

        if (Files.exists(assetIndexFile) && !forceDownloading) {
            // verify correctness of file content
            if (verifyHashCode) {
                try {
                    String actualSum = DigestUtils.digestToString("SHA-1", assetIndexFile);
                    if (actualSum.equalsIgnoreCase(assetIndexInfo.getSha1()))
                        return null;
                } catch (IOException e) {
                    LOG.warning("Failed to calculate sha1sum of file " + assetIndexInfo, e);
                    // continue downloading
                }
            } else {
                try {
                    JsonUtils.fromNonNullJson(Files.readString(assetIndexFile), AssetIndex.class);
                    return null;
                } catch (IOException | JsonParseException ignore) {
                }
            }
        }

        // We should not check the hash code of asset index file since this file is not consistent
        // And Mojang will modify this file anytime. So assetIndex.hash might be outdated.
        DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
        var task = new FileDownloadTask(
                downloadProvider.getDownloadCandidates(assetIndexInfo.getUrl()),
                assetIndexFile,
                verifyHashCode ? new FileDownloadTask.IntegrityCheck("SHA-1", assetIndexInfo.getSha1()) : null
        );
        task.setCacheRepository(dependencyManager.getCacheRepository());
        return context.run(task);
    }

    public static class GameAssetIndexMalformedException extends IOException {
    }
}
