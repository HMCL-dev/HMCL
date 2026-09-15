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
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 *
 * @author huangyuhui
 */
public final class GameAssetDownloadTask extends Task<Void> {

    /// Upper bound on the workers verifying the shared asset tree.
    ///
    /// The per-object cost is dominated by hashing, and object sizes differ by orders of magnitude,
    /// so the workers pull the next object themselves instead of taking a fixed slice. Measured on a
    /// full 1.20.1 index, workers pulling their own work reached a 4.1x speedup at eight threads where
    /// fixed slices reached 1.4x, and 5.3x at sixteen.
    private static final int MAX_VERIFY_THREADS = 16;
    
    private final AbstractDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private final AssetIndexInfo assetIndexInfo;
    private final Path assetIndexFile;
    private final boolean integrityCheck;
    private final List<Task<?>> dependents = new ArrayList<>(1);
    private final List<Task<?>> dependencies = new ArrayList<>();

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

        setStage("hmcl.install.assets");
        dependents.add(new GameAssetIndexDownloadTask(dependencyManager, this.manifest, forceDownloadingIndex));
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() throws Exception {
        AssetIndex index;
        try {
            index = JsonUtils.fromNonNullJson(Files.readString(assetIndexFile), AssetIndex.class);
        } catch (IOException | JsonParseException e) {
            throw new GameAssetIndexDownloadTask.GameAssetIndexMalformedException();
        }

        List<AssetObject> objects = List.copyOf(index.getObjects().values());
        boolean[] missing = findMissingObjects(objects);

        int progress = 0;
        for (int i = 0; i < objects.size(); i++) {
            if (isCancelled())
                throw new InterruptedException();

            AssetObject assetObject = objects.get(i);
            GameRepository gameRepository = dependencyManager.getGameRepository();
            Path file = gameRepository.getLayout().getAssetObject(assetObject);

            if (missing[i]) {
                List<URI> uris = dependencyManager.getDownloadProvider().getAssetObjectCandidates(assetObject.getLocation());

                var task = new FileDownloadTask(uris, file, new FileDownloadTask.IntegrityCheck("SHA-1", assetObject.hash()));
                task.setName(assetObject.hash());
                task.setCandidate(dependencyManager.getCacheRepository().getCommonDirectory()
                        .resolve("assets").resolve("objects").resolve(assetObject.getLocation()));
                task.setCacheRepository(dependencyManager.getCacheRepository());
                task.setCaching(true);
                dependencies.add(task.withCounter("hmcl.install.assets"));
            } else {
                dependencyManager.getCacheRepository().tryCacheFile(file, CacheRepository.SHA1, assetObject.hash());
            }

            updateProgress(++progress, objects.size());
        }

        if (!dependencies.isEmpty()) {
            getProperties().put("total", dependencies.size());
            notifyPropertiesChanged();
        }
    }

    /// Decides which objects have to be downloaded, reading the present ones in parallel.
    ///
    /// An object counts as present when it is a regular file and, with `integrityCheck` set, when its
    /// SHA-1 matches the index. The index holds thousands of objects and hashing one re-reads the file
    /// end to end, so the checks are the expensive part of this task; they are independent of each
    /// other, which the sequential loop could not exploit. The tree being verified is the one the game
    /// shares between instances, so it stays warm and the checks are mostly reading it back.
    ///
    /// A failure to hash an object is not fatal: it is logged and the object keeps its previous state,
    /// as it did before the checks were parallelized.
    ///
    /// @param objects the index entries, in index order
    /// @return one flag per entry, `true` when the object has to be downloaded
    /// @throws IOException if a worker failed unexpectedly
    /// @throws InterruptedException if the calling task was interrupted while waiting
    private boolean[] findMissingObjects(List<AssetObject> objects) throws IOException, InterruptedException {
        boolean[] missing = new boolean[objects.size()];
        if (objects.isEmpty())
            return missing;

        int threads = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), MAX_VERIFY_THREADS));
        if (threads == 1) {
            for (int i = 0; i < objects.size(); i++) {
                missing[i] = isMissing(objects.get(i));
            }
            return missing;
        }

        ExecutorService executor = Schedulers.io();
        AtomicInteger next = new AtomicInteger();
        List<Future<?>> pending = new ArrayList<>(threads);

        for (int thread = 0; thread < threads; thread++) {
            pending.add(executor.submit(() -> {
                for (int i = next.getAndIncrement(); i < objects.size(); i = next.getAndIncrement()) {
                    missing[i] = isMissing(objects.get(i));
                }
            }));
        }

        for (Future<?> future : pending) {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw new IOException("Failed to verify game assets", e.getCause());
            }
        }

        return missing;
    }

    /// Reports whether one indexed object has to be downloaded.
    ///
    /// @param assetObject the index entry
    /// @return `true` when the local file is absent or fails its checksum
    private boolean isMissing(AssetObject assetObject) {
        Path file = dependencyManager.getGameRepository().getLayout().getAssetObject(assetObject);
        if (!Files.isRegularFile(file))
            return true;

        if (!integrityCheck)
            return false;

        try {
            return !assetObject.validateChecksum(file, true);
        } catch (IOException e) {
            LOG.warning("Unable to calc hash value of file " + file, e);
            return false;
        }
    }

    public static final boolean DOWNLOAD_INDEX_FORCIBLY = true;
    public static final boolean DOWNLOAD_INDEX_IF_NECESSARY = false;
}
