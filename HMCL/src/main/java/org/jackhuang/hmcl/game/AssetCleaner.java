// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.function.LongConsumer;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Removes asset indexes and asset objects of a repository that no instance refers to.
///
/// All instances of a repository share one `assets` directory and nothing removes the files of
/// versions that were uninstalled again. The reference set comes from the repository model, not
/// from the version JSON files: [GameInstance#getResolvedManifest] resolves `inheritsFrom`, and
/// [GameInstanceManifest#getAssetIndex] applies the same legacy fallbacks the launcher uses.
///
/// Scanning never deletes anything, and refuses to return a summary when the reference set may be
/// incomplete: deleting an asset a version still needs is not recoverable offline.
@NotNullByDefault
public final class AssetCleaner {

    private static final String JSON_SUFFIX = ".json";

    /// Length of a SHA-1 hash in hexadecimal notation.
    private static final int SHA1_LENGTH = 40;

    /// Synthetic ids used only to ask a layout where it stores instances, indexes and objects.
    private static final String PROBE_ID = "hmcl-asset-cleanup-probe";
    private static final String PROBE_HASH = "0".repeat(SHA1_LENGTH);

    private AssetCleaner() {
    }

    /// The reclaimable assets reported by [AssetCleaner#scan].
    ///
    /// @param files the number of files that would be deleted
    /// @param bytes the total size of those files
    public record Summary(int files, long bytes) {

        /// Returns whether the asset directory only holds referenced files.
        ///
        /// @return whether there is nothing to delete
        public boolean isClean() {
            return files == 0;
        }
    }

    /// Counts the assets of the repository that no instance refers to.
    ///
    /// @param repository the repository to examine
    /// @return the reclaimable assets
    /// @throws IOException if the reference set cannot be determined reliably
    public static Summary scan(GameRepository repository) throws IOException {
        Plan plan = analyze(repository);
        return new Summary(plan.fileCount(), plan.bytes());
    }

    /// Deletes the assets of the repository that no instance refers to.
    ///
    /// @param repository the repository to clean
    /// @param progress   receives the number of processed entries, or `null` to ignore progress
    /// @return the number of bytes that were actually reclaimed
    /// @throws IOException if an entry exists but cannot be deleted
    public static long clean(GameRepository repository, @Nullable LongConsumer progress) throws IOException {
        return delete(analyze(repository), progress != null ? progress : done -> {
        });
    }

    /// Creates a task that deletes the unused assets and reports progress.
    ///
    /// Cancellation is observed between two entries. The result is the number of reclaimed bytes.
    ///
    /// @param repository the repository to clean
    /// @return the task, not started yet
    public static Task<Long> cleanTask(GameRepository repository) {
        return new Task<Long>() {
            @Override
            public void execute() throws Exception {
                Plan plan = analyze(repository);
                long total = plan.entryCount();

                setResult(total == 0L ? 0L : delete(plan, done -> {
                    if (isCancelled()) {
                        throw new CancellationException();
                    }
                    updateProgress(done, total);
                }));
            }
        }.setName("assets.cleanup");
    }

    /// The entries a scan decided to delete.
    private record Plan(
            List<Path> indexFiles,
            List<Path> objects,
            List<Path> buckets,
            List<Path> virtualDirs,
            long bytes
    ) {
        int fileCount() {
            return indexFiles.size() + objects.size();
        }

        int entryCount() {
            return fileCount() + buckets.size() + virtualDirs.size();
        }
    }

    private static Plan analyze(GameRepository repository) throws IOException {
        GameRepositoryLayout layout = repository.getLayout();
        GameRepositorySnapshot snapshot = repository.getSnapshot();

        requireAllInstancesLoaded(layout, snapshot);

        Set<String> usedAssetIds = new TreeSet<>();
        for (GameInstance instance : snapshot.getInstances()) {
            try {
                usedAssetIds.add(instance.getResolvedManifest().getAssetIndex().getId());
            } catch (NoSuchGameInstanceException e) {
                throw new IOException("Cannot resolve the inherited manifest of instance " + instance.getId(), e);
            }
        }
        if (usedAssetIds.isEmpty()) {
            throw new IOException("No game instance is loaded, refusing to clean the shared asset directory");
        }

        Set<String> liveHashes = collectLiveHashes(layout, usedAssetIds);
        Path assetDirectory = layout.getAssetDirectory();
        long bytes = 0L;

        List<Path> indexFiles = new ArrayList<>();
        Path indexesDirectory = Objects.requireNonNull(layout.getAssetIndexFile(PROBE_ID).getParent());
        for (Path file : list(indexesDirectory)) {
            String name = file.getFileName().toString();
            if (!name.endsWith(JSON_SUFFIX) || !Files.isRegularFile(file)) {
                continue;
            }
            if (!usedAssetIds.contains(name.substring(0, name.length() - JSON_SUFFIX.length()))) {
                indexFiles.add(file);
                bytes += size(file);
            }
        }

        List<Path> virtualDirs = new ArrayList<>();
        for (Path directory : list(assetDirectory.resolve("virtual"))) {
            if (Files.isDirectory(directory) && !usedAssetIds.contains(directory.getFileName().toString())) {
                virtualDirs.add(directory);
            }
        }

        List<Path> objects = new ArrayList<>();
        List<Path> buckets = new ArrayList<>();
        @Nullable Path objectsDirectory = objectsDirectory(layout, assetDirectory);
        if (objectsDirectory != null) {
            for (Path entry : list(objectsDirectory)) {
                if (Files.isRegularFile(entry)) {
                    objects.add(entry);
                    bytes += size(entry);
                    continue;
                }
                if (!Files.isDirectory(entry)) {
                    continue;
                }

                String bucketName = entry.getFileName().toString();
                int entries = 0;
                int orphans = 0;
                for (Path file : list(entry)) {
                    if (!Files.isRegularFile(file)) {
                        continue;
                    }
                    entries++;
                    if (isLiveObject(file.getFileName().toString(), bucketName, liveHashes)) {
                        continue;
                    }
                    orphans++;
                    objects.add(file);
                    bytes += size(file);
                }
                if (entries > 0 && entries == orphans) {
                    buckets.add(entry);
                }
            }
        }

        return new Plan(indexFiles, objects, buckets, virtualDirs, bytes);
    }

    /// Deletes a plan. Objects go before the indexes that described them, so an interrupted run
    /// never leaves an index pointing at objects that are already gone. Entries that disappeared
    /// since the analysis are skipped instead of failing the run.
    private static long delete(Plan plan, LongConsumer progress) throws IOException {
        long done = 0L;
        long bytes = 0L;

        for (Path file : plan.objects()) {
            bytes += deleteFile(file);
            progress.accept(++done);
        }
        for (Path directory : plan.buckets()) {
            if (isEmptyDirectory(directory)) {
                Files.deleteIfExists(directory);
            }
            progress.accept(++done);
        }
        for (Path directory : plan.virtualDirs()) {
            if (Files.isDirectory(directory)) {
                FileUtils.deleteDirectory(directory);
            }
            progress.accept(++done);
        }
        for (Path file : plan.indexFiles()) {
            bytes += deleteFile(file);
            progress.accept(++done);
        }

        LOG.info("[Assets] Reclaimed " + FileUtils.parseFileSize(bytes) + " from " + plan.fileCount() + " file(s)");
        return bytes;
    }

    /// Returns the object hashes referenced by the passed asset index ids.
    private static Set<String> collectLiveHashes(GameRepositoryLayout layout, Set<String> usedAssetIds) throws IOException {
        Set<String> liveHashes = new HashSet<>();

        for (String assetId : usedAssetIds) {
            Path indexFile = layout.getAssetIndexFile(assetId);
            if (!Files.isRegularFile(indexFile)) {
                // Never downloaded; the launcher fetches it on demand.
                continue;
            }

            @Nullable AssetIndex index;
            try {
                index = JsonUtils.fromJsonFile(indexFile, AssetIndex.class);
            } catch (JsonParseException e) {
                throw new IOException("Malformed asset index " + indexFile + ", repair it before cleaning assets", e);
            }
            if (index == null) {
                throw new IOException("Malformed asset index " + indexFile + ", repair it before cleaning assets");
            }

            for (AssetObject object : index.getObjects().values()) {
                if (object != null) {
                    liveHashes.add(object.hash().toLowerCase(Locale.ROOT));
                }
            }
        }

        return liveHashes;
    }

    /// Fails when an instance directory on disk is not part of the snapshot. The launcher logs and
    /// skips directories it cannot load, and such an instance still owns an asset index.
    private static void requireAllInstancesLoaded(GameRepositoryLayout layout, GameRepositorySnapshot snapshot) throws IOException {
        @Nullable Path instancesDirectory = layout.getInstanceRoot(new GameInstanceID(PROBE_ID)).getParent();
        if (instancesDirectory == null) {
            return;
        }

        for (Path directory : list(instancesDirectory)) {
            String name = directory.getFileName().toString();
            if (!Files.isDirectory(directory) || name.startsWith(".") || !GameInstanceID.isValid(name)
                    || snapshot.findInstance(new GameInstanceID(name)) != null) {
                continue;
            }

            for (Path file : list(directory)) {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(JSON_SUFFIX)) {
                    throw new IOException("Instance " + name + " is not loaded, refresh the game directory before cleaning assets");
                }
            }
        }
    }

    /// Returns the object directory of the layout, or `null` when it does not bucket objects by hash.
    private static @Nullable Path objectsDirectory(GameRepositoryLayout layout, Path assetDirectory) {
        @Nullable Path bucket = layout.getAssetObject(new AssetObject(PROBE_HASH, 0L)).getParent();
        if (bucket == null || bucket.getFileName() == null || !PROBE_HASH.startsWith(bucket.getFileName().toString())) {
            LOG.warning("[Assets] Unsupported asset layout below " + assetDirectory + ": objects are not bucketed by hash");
            return null;
        }

        @Nullable Path objectsDirectory = bucket.getParent();
        return objectsDirectory != null && objectsDirectory.startsWith(assetDirectory)
                ? objectsDirectory
                : assetDirectory.resolve("objects");
    }

    /// Returns whether the file is referenced and sits in the bucket named after its hash.
    private static boolean isLiveObject(String fileName, String bucketName, Set<String> liveHashes) {
        if (fileName.length() != SHA1_LENGTH || bucketName.length() != 2) {
            return false;
        }

        String hash = fileName.toLowerCase(Locale.ROOT);
        return hash.startsWith(bucketName.toLowerCase(Locale.ROOT)) && liveHashes.contains(hash);
    }

    /// Deletes a file and returns its size before deletion, or `0` when it no longer existed.
    private static long deleteFile(Path file) throws IOException {
        long size = size(file);
        return Files.deleteIfExists(file) ? size : 0L;
    }

    /// Lists the direct children of a directory, empty when it does not exist.
    private static List<Path> list(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(directory)) {
            return stream.toList();
        }
    }

    private static boolean isEmptyDirectory(Path directory) throws IOException {
        return Files.isDirectory(directory) && list(directory).isEmpty();
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }
}
