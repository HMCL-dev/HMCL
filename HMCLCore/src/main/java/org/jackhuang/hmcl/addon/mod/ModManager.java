/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.addon.mod;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.addon.meta.*;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.NoSuchGameInstanceException;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ModManager extends LocalAddonManager<LocalModFile> {
    public static final List<String> MOD_EXTENSIONS = List.of("jar", "zip", "litemod");

    @FunctionalInterface
    private interface ModMetadataReader {
        LocalModFile fromFile(ModManager modManager, Path modFile, ZipFileTree tree) throws IOException, JsonParseException;
    }

    private static final Map<String, List<Pair<ModMetadataReader, ModLoaderType>>> READERS;

    static {
        var map = new HashMap<String, List<Pair<ModMetadataReader, ModLoaderType>>>();
        var zipReaders = List.<Pair<ModMetadataReader, ModLoaderType>>of(
                pair(ForgeNewModMetadata::fromForgeFile, ModLoaderType.FORGE),
                pair(ForgeNewModMetadata::fromNeoForgeFile, ModLoaderType.NEO_FORGE),
                pair(ForgeOldModMetadata::fromFile, ModLoaderType.FORGE),
                pair(FabricModMetadata::fromFile, ModLoaderType.FABRIC),
                pair(QuiltModMetadata::fromFile, ModLoaderType.QUILT)
        );

        map.put("jar", zipReaders);
        map.put("zip", zipReaders);
        map.put("litemod", List.of(pair(LiteModMetadata::fromFile, ModLoaderType.LITE_LOADER)));

        READERS = map;
    }

    private String gameVersion;
    private final HashMap<Pair<String, ModLoaderType>, LocalMod> localMods = new HashMap<>();
    private final EnumSet<ModLoaderType> supportedLoaders = EnumSet.noneOf(ModLoaderType.class);
    private GameComponentAnalyzer analyzer;

    private boolean loaded = false;

    /// Whether to perform strict integrity checking (CRC32 verification).
    ///
    /// Written when the strict-mode setting changes and read by integrity check tasks, so it
    /// must be volatile.
    private volatile boolean strictIntegrityCheck = false;

    /// Generation counter incremented on every refresh.
    ///
    /// Integrity check tasks capture the generation at scheduling time and discard their
    /// result if the generation has advanced, preventing stale results from being written
    /// back to mods that a later refresh has already replaced.
    private final AtomicLong refreshGeneration = new AtomicLong();

    /// In-flight integrity checks, keyed by the file being checked.
    ///
    /// Keying by file guarantees at most one in-flight check per file, so a slower earlier
    /// check can never finish later and overwrite a newer result for the same file.
    private final Map<Path, PendingCheck> pendingIntegrityChecks = new HashMap<>();

    /// Cache for integrity check results, keyed by (path, lastModified, size).
    ///
    /// The key deliberately omits content hashing: hashing would require reading every mod
    /// on every scan, negating the cache. An attacker able to rewrite a mod while preserving
    /// its size and mtime already has write access to the mods directory, so this is not a
    /// meaningful bypass for a reference-only check.
    ///
    /// Each value records the strict flag it was computed with, so a result produced in one
    /// mode is never reused for a check in the other.
    private final Map<CacheKey, CacheValue> integrityCheckCache = new ConcurrentHashMap<>();

    /// Executor for integrity checks.
    ///
    /// Uses the shared I/O scheduler rather than a per-instance pool: managers are created
    /// lazily per snapshot member and replaced on every repository refresh, so a per-instance
    /// pool would leak its threads on each replacement.
    private static final Executor INTEGRITY_CHECK_EXECUTOR = Schedulers.io();

    /// Maximum time to wait for pending integrity checks before giving up.
    private static final long INTEGRITY_CHECK_TIMEOUT_SECONDS = 30;

    /// Cache key for integrity check results.
    ///
    /// All paths reaching this cache come from the same directory scan, so they share a single
    /// path form and `Path.equals` is reliable here.
    private record CacheKey(Path path, long lastModified, long size) {
    }

    /// A cached integrity check result together with the mode that produced it.
    ///
    /// @param strict whether the result was computed with CRC32 verification enabled
    /// @param corrupt whether the archive was found to be damaged
    private record CacheValue(boolean strict, boolean corrupt) {
    }

    /// An in-flight integrity check.
    ///
    /// @param future completes when the check finishes
    /// @param modInfo the entry the result will be written to
    private record PendingCheck(CompletableFuture<Void> future, LocalModFile modInfo) {
    }

    /// Creates a mod manager for the given instance.
    ///
    /// @param instance the snapshot member whose mods directory this manager operates on
    public ModManager(DefaultGameInstance instance) {
        super(instance);
    }

    @Override
    public Path getDirectory() {
        return instance.getModsDirectory();
    }

    public GameComponentAnalyzer getComponentAnalyzer() {
        lock.lock();
        try {
            return analyzer;
        } finally {
            lock.unlock();
        }
    }

    public LocalMod getLocalMod(String modId, ModLoaderType modLoaderType) {
        lock.lock();
        try {
            return localMods.computeIfAbsent(pair(modId, modLoaderType),
                    x -> new LocalMod(x.getKey(), x.getValue()));
        } finally {
            lock.unlock();
        }
    }

    public boolean hasMod(String modId, ModLoaderType modLoaderType) {
        lock.lock();
        try {
            return localMods.containsKey(pair(modId, modLoaderType));
        } finally {
            lock.unlock();
        }
    }

    public String getGameVersion() {
        lock.lock();
        try {
            return gameVersion;
        } finally {
            lock.unlock();
        }
    }

    public EnumSet<ModLoaderType> getSupportedLoaders() {
        lock.lock();
        try {
            return EnumSet.copyOf(supportedLoaders);
        } finally {
            lock.unlock();
        }
    }

    /// Reads the metadata of the given mod file and adds it to [localFiles].
    ///
    /// The entry is only added when it is not an old (`.old`) file, so a rollback candidate
    /// stays out of the visible mod list.
    ///
    /// @param file the mod file to read
    /// @param fileInfo the file's identity, already read by the directory scan
    private void addModInfo(Path file, FileInfo fileInfo) {
        String fileName = StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION, OLD_EXTENSION);
        // Lower-cased so the lookup agrees with isModFile, which decides which files reach here
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);

        List<Pair<ModMetadataReader, ModLoaderType>> readersMap = READERS.get(extension);
        if (readersMap == null) {
            // Is not a mod file.
            return;
        }

        Set<ModLoaderType> modLoaderTypes = instance.getModLoaders();

        var supportedReaders = new ArrayList<ModMetadataReader>();
        var unsupportedReaders = new ArrayList<ModMetadataReader>();

        for (Pair<ModMetadataReader, ModLoaderType> reader : readersMap) {
            if (modLoaderTypes.contains(reader.getValue())) {
                supportedReaders.add(reader.getKey());
            } else {
                unsupportedReaders.add(reader.getKey());
            }
        }

        LocalModFile modInfo = null;

        List<Exception> exceptions = new ArrayList<>();
        try (ZipFileTree tree = CompressingUtils.openZipTree(file)) {
            for (ModMetadataReader reader : supportedReaders) {
                try {
                    modInfo = reader.fromFile(this, file, tree);
                    break;
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }

            if (modInfo == null) {
                for (ModMetadataReader reader : unsupportedReaders) {
                    try {
                        modInfo = reader.fromFile(this, file, tree);
                        break;
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            LOG.warning("Failed to open mod file " + file, e);
        }

        boolean metadataReadable = modInfo != null;

        Exception exception = null;
        if (modInfo == null) {
            exception = new Exception("Failed to read mod metadata");
            for (Exception e : exceptions) {
                exception.addSuppressed(e);
            }
            LOG.warning("Failed to read mod metadata", exception);

            String fileNameWithoutExtension = FileUtils.getNameWithoutExtension(file);

            modInfo = new LocalModFile(this,
                    getLocalMod(fileNameWithoutExtension, ModLoaderType.UNKNOWN),
                    file,
                    fileNameWithoutExtension,
                    new LocalAddonFile.Description("litemod".equals(extension) ? "LiteLoader Mod" : ""),
                    "", "", "", "", "",
                    false
            );
        }

        // Record the identity read by the directory scan, so the metadata does not have to be
        // read a second time inside the entry
        modInfo.setFileInfo(fileInfo);

        // Schedule the integrity check before the metadata-readable branch, so that a mod
        // whose metadata is unreadable (e.g. because the archive is damaged) is still checked.
        // A corrupt archive fails metadata reading, so gating the check on metadata readability
        // would skip every mod that most needs checking.
        if (!modInfo.isOld()) {
            scheduleIntegrityCheck(file, fileInfo, modInfo);
        }

        if (!metadataReadable) {
            // The archive may be structurally sound while its metadata is unreadable; the
            // entry is still displayed as an unknown mod, which is the existing signal for
            // that. It must not be folded into integrityCheckFailed, which reports only
            // whether the integrity check reached a verdict.
            LOG.warning("Mod metadata is unreadable: " + file, exception);
        }

        if (!modInfo.isOld()) {
            localFiles.add(modInfo);
        }
    }

    /// Sets whether to perform strict integrity checking (CRC32 verification).
    ///
    /// Changing the mode invalidates every cached result and every in-flight check, because
    /// both were computed under the previous mode. The already-loaded entries are reset to
    /// "unknown" at the same time, so the UI never keeps showing a verdict produced by the
    /// mode the user just left. In-flight checks are cancelled so their work is not wasted.
    ///
    /// @param strict whether to enable strict mode
    public void setStrictIntegrityCheck(boolean strict) {
        lock.lock();
        try {
            if (this.strictIntegrityCheck != strict) {
                this.strictIntegrityCheck = strict;
                integrityCheckCache.clear();
                refreshGeneration.incrementAndGet();
                for (PendingCheck check : pendingIntegrityChecks.values()) {
                    check.future().cancel(true);
                }
                pendingIntegrityChecks.clear();
                for (LocalModFile localFile : localFiles) {
                    localFile.setCorrupt(false);
                    localFile.setIntegrityCheckFailed(true);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /// Schedules an async integrity check for the given mod file.
    ///
    /// At most one check per file is in flight. A refresh that finds the same path already
    /// registered (i.e. a previous refresh's check is still running) attaches to that check
    /// instead of starting a second one, so the same file is never checked twice concurrently.
    ///
    /// Results are cached by (path, lastModified, size) and by the mode that produced them,
    /// so a result computed in one mode is never reused for a check in the other. A cache hit
    /// is resolved synchronously, without submitting a task.
    ///
    /// Only [ModIntegrityChecker.Result#INTACT] and [ModIntegrityChecker.Result#CORRUPT] are
    /// cached: an [ModIntegrityChecker.Result#UNKNOWN] result means the check could not be
    /// performed, so it is neither cached nor treated as evidence that the mod is sound.
    ///
    /// The task captures the current refresh generation and discards its result if a later
    /// refresh (or strict-mode change) has advanced the generation, so stale results are never
    /// written back to mods that have already been replaced.
    ///
    /// Must be called while holding [lock].
    ///
    /// @param file the mod file to check
    /// @param fileInfo the file's identity, as read by the directory scan
    /// @param modInfo the LocalModFile to update with the result
    private void scheduleIntegrityCheck(Path file, FileInfo fileInfo, LocalModFile modInfo) {
        PendingCheck existing = pendingIntegrityChecks.get(file);
        if (existing != null) {
            // A check for this file is already running. Its result is written to the entry it
            // was scheduled for, so a different entry for the same path would otherwise never
            // receive a verdict and stay "unverified" forever. Copy the in-flight check's
            // outcome onto this entry too.
            existing.future().whenComplete((ignored, error) -> {
                if (error == null) {
                    modInfo.setCorrupt(existing.modInfo().isCorrupt());
                    modInfo.setIntegrityCheckFailed(existing.modInfo().isIntegrityCheckFailed());
                } else {
                    modInfo.setIntegrityCheckFailed(true);
                }
            });
            return;
        }

        CacheKey key = new CacheKey(file, fileInfo.lastModified(), fileInfo.size());

        // Resolve a cache hit without submitting a task
        CacheValue cached = integrityCheckCache.get(key);
        if (cached != null && cached.strict() == strictIntegrityCheck) {
            modInfo.setCorrupt(cached.corrupt());
            modInfo.setIntegrityCheckFailed(false);
            return;
        }

        final long generation = refreshGeneration.get();
        final boolean strict = strictIntegrityCheck;
        CompletableFuture<Void> checkFuture = CompletableFuture.runAsync(() -> {
            ModIntegrityChecker.Result result = ModIntegrityChecker.check(file, strict);
            lock.lock();
            try {
                if (refreshGeneration.get() != generation) {
                    return;
                }
                switch (result) {
                    case INTACT -> {
                        integrityCheckCache.put(key, new CacheValue(strict, false));
                        modInfo.setCorrupt(false);
                        modInfo.setIntegrityCheckFailed(false);
                    }
                    case CORRUPT -> {
                        integrityCheckCache.put(key, new CacheValue(strict, true));
                        modInfo.setCorrupt(true);
                        modInfo.setIntegrityCheckFailed(false);
                    }
                    case UNKNOWN -> {
                        // The check could not be performed, so the mod's state is unknown. Leave
                        // the previous verdict alone: it is the best information available, and
                        // reporting "sound" would be an unsupported claim.
                        modInfo.setIntegrityCheckFailed(true);
                    }
                }
            } catch (Exception e) {
                LOG.warning("Failed to check mod integrity: " + file, e);
                if (refreshGeneration.get() == generation) {
                    modInfo.setIntegrityCheckFailed(true);
                }
            } finally {
                lock.unlock();
            }
        }, INTEGRITY_CHECK_EXECUTOR);
        pendingIntegrityChecks.put(file, new PendingCheck(checkFuture, modInfo));
    }

    public void analyze() throws IOException {
        lock.lock();
        try {
            gameVersion = instance.getVersion().toString();
            analyzer = instance.getAnalyzer();
        } catch (NoSuchGameInstanceException e) {
            throw new IOException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void refresh() throws IOException {
        lock.lock();
        try {
            // Advance the generation so in-flight checks from the previous scan discard their results
            refreshGeneration.incrementAndGet();

            // Cancel all pending integrity checks before clearing
            for (PendingCheck check : pendingIntegrityChecks.values()) {
                check.future().cancel(true);
            }
            pendingIntegrityChecks.clear();

            // Analyze first to determine if subfolders are supported
            analyze();

            boolean supportSubfolders = analyzer.has(GameComponentType.FORGE)
                    || analyzer.has(GameComponentType.QUILT)
                    || analyzer.has(GameComponentType.CLEANROOM)
                    || analyzer.has(GameComponentType.LITELOADER);

            // Single directory traversal: its result drives both the change detection below and
            // the metadata scan further down, so the mods directory is never walked twice.
            Map<Path, FileInfo> currentFiles = new LinkedHashMap<>();
            if (Files.isDirectory(getDirectory())) {
                collectModFiles(getDirectory(), currentFiles, supportSubfolders);
            }

            // Evict cache entries whose file is gone, and any entry for a path whose file
            // identity changed, so at most one entry per path is retained.
            integrityCheckCache.keySet().removeIf(key -> {
                FileInfo info = currentFiles.get(key.path());
                return info == null || info.lastModified() != key.lastModified() || info.size() != key.size();
            });

            // If files haven't changed and we already have loaded data, skip rescan.
            // A file is unchanged only when its identity (lastModified and size) matches the
            // value recorded when it was loaded, so an in-place replacement under the same
            // name is still detected. `currentFiles` also contains `.old` backups, which are
            // not in `localFiles`, so compare against the loaded set rather than the raw count.
            if (loaded && allFilesUnchanged(currentFiles)) {
                // Re-schedule integrity checks for all loaded mods. Files are unchanged, so
                // this resolves from the cache for any mod already checked; only mods whose
                // result is missing or was computed in another mode run a real check.
                for (LocalModFile localFile : localFiles) {
                    FileInfo info = currentFiles.get(localFile.getFile());
                    if (info != null) {
                        scheduleIntegrityCheck(localFile.getFile(), info, localFile);
                    }
                }
                // The loader set depends on which mods are present, and it is read by the
                // mod list page; keep it correct on this path too.
                updateSupportedLoaders();
                return;
            }

            localFiles.clear();
            localMods.clear();

            for (Map.Entry<Path, FileInfo> entry : currentFiles.entrySet()) {
                addModInfo(entry.getKey(), entry.getValue());
            }

            updateSupportedLoaders();

            loaded = true;
        } finally {
            lock.unlock();
        }
    }

    /// Returns whether every file [addModInfo] loaded is still present with the same identity.
    ///
    /// Only the entries in [localFiles] are compared: [collectModFiles] also reports `.old`
    /// backups and files that [addModInfo] skipped (unknown extension), which are legitimately
    /// absent from [localFiles] and must not by themselves force a rescan.
    ///
    /// Must be called while holding [lock].
    ///
    /// @param currentFiles the files found by the latest directory scan
    /// @return `true` if no loaded mod was added, removed, or replaced
    private boolean allFilesUnchanged(Map<Path, FileInfo> currentFiles) {
        for (LocalModFile localFile : localFiles) {
            FileInfo currentInfo = currentFiles.get(localFile.getFile());
            if (currentInfo == null || !currentInfo.equals(localFile.getFileInfo())) {
                return false;
            }
        }
        // A file that [addModInfo] would now load but did not load before must also force a rescan
        int loadable = 0;
        for (Map.Entry<Path, FileInfo> entry : currentFiles.entrySet()) {
            if (!ModManager.isOldFile(entry.getKey())) {
                loadable++;
            }
        }
        return loadable == localFiles.size();
    }

    /// Returns whether the given path is an `.old` backup file.
    ///
    /// @param file the path to test
    /// @return `true` if the file name carries the old-file suffix
    private static boolean isOldFile(Path file) {
        return FileUtils.getName(file).endsWith(OLD_EXTENSION);
    }

    /// Collects the identity of every file that [addModInfo] must be called for.
    ///
    /// This covers the mods directory plus one level of subdirectories when the instance
    /// supports them. The traversal is deliberately iterative and depth-limited rather than
    /// recursive, so a deep directory tree cannot overflow the stack, and it does not follow
    /// symbolic links, so it cannot leave the mods directory.
    ///
    /// `.old` backup files are included: [addModInfo] registers them with their [LocalMod]
    /// (via the [LocalModFile] constructor) as rollback candidates while keeping them out of
    /// [localFiles], so they must still reach it. The `.disabled` suffix is stripped before
    /// the extension check, matching [addModInfo]'s selection.
    ///
    /// @param directory the mods directory to scan
    /// @param files the map to store each mod file's path and identity in
    /// @param supportSubfolders whether to scan one level of subdirectories
    /// @throws IOException if an I/O error occurs
    private void collectModFiles(Path directory, Map<Path, FileInfo> files, boolean supportSubfolders) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (supportSubfolders && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)
                        && !".connector".equalsIgnoreCase(entry.getFileName().toString())) {
                    try (DirectoryStream<Path> subStream = Files.newDirectoryStream(entry)) {
                        for (Path subEntry : subStream) {
                            if (isModFile(subEntry)) {
                                files.put(subEntry, FileInfo.of(subEntry));
                            }
                        }
                    }
                } else if (isModFile(entry)) {
                    files.put(entry, FileInfo.of(entry));
                }
            }
        }
    }

    /// Returns whether [addModInfo] must be called for the given path.
    ///
    /// This mirrors [addModInfo]'s own selection exactly — the same `.disabled` suffix
    /// stripping and the same case-insensitive extension comparison — so the change detection
    /// in [refresh] sees precisely the files that get loaded. `.old` files are included
    /// because [addModInfo] registers them as rollback candidates.
    ///
    /// @param entry the path to test
    /// @return `true` if the path is a regular file that [addModInfo] would process
    private static boolean isModFile(Path entry) {
        if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        String name = entry.getFileName().toString();
        String baseName = StringUtils.removeSuffix(name, DISABLED_EXTENSION, OLD_EXTENSION);
        String ext = FileUtils.getExtension(baseName).toLowerCase(Locale.ROOT);
        return MOD_EXTENSIONS.contains(ext);
    }

    /// The identity of a file, used to detect in-place replacement.
    ///
    /// Package-private so it stays an implementation detail shared with [LocalModFile] rather
    /// than becoming part of the public API surface.
    ///
    /// @param lastModified the file's last-modified time in milliseconds
    /// @param size the file's size in bytes
    record FileInfo(long lastModified, long size) {
        /// Reads the identity of the given file.
        ///
        /// @param file the file to read
        /// @return the file's identity
        /// @throws IOException if the file's metadata cannot be read
        static FileInfo of(Path file) throws IOException {
            return new FileInfo(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        }
    }

    @Override
    public Comparator<LocalModFile> getComparator() {
        return LocalModFile::compareTo;
    }

    public @Unmodifiable List<LocalModFile> getLocalFiles() throws IOException {
        lock.lock();
        try {
            if (!loaded)
                refresh();
            return super.getLocalFiles();
        } finally {
            lock.unlock();
        }
    }

    /// Waits for all pending integrity checks to complete.
    ///
    /// Should be called before reading corrupt status to ensure all async checks are done.
    ///
    /// The wait deliberately does not join a barrier on the FX thread: this method is called
    /// from the launch pipeline thread, and blocking that thread on the FX thread risks a
    /// deadlock when the FX thread is itself waiting on the launch.
    ///
    /// Each check is awaited individually rather than through `allOf`, because `allOf`
    /// completes exceptionally as soon as any member fails and would then skip waiting for
    /// the rest. A timeout bounds the overall wait so a check stuck on a stalled filesystem
    /// cannot hang the launch.
    ///
    /// Any mod whose result did not arrive — because it timed out, failed, or the wait was
    /// interrupted — is marked as unverified, so it is never silently reported as sound. A check
    /// cancelled by a concurrent refresh is deliberately left alone: it has already been
    /// rescheduled for the same file. Finished entries are removed afterwards so the map does not
    /// grow across refreshes.
    public void waitForIntegrityChecks() {
        List<Map.Entry<Path, PendingCheck>> checks;
        lock.lock();
        try {
            if (pendingIntegrityChecks.isEmpty()) {
                return;
            }
            checks = new ArrayList<>(pendingIntegrityChecks.entrySet());
        } finally {
            lock.unlock();
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(INTEGRITY_CHECK_TIMEOUT_SECONDS);
        List<Map.Entry<Path, PendingCheck>> unfinished = new ArrayList<>();
        boolean interrupted = false;
        for (Map.Entry<Path, PendingCheck> entry : checks) {
            PendingCheck check = entry.getValue();
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                unfinished.add(entry);
                continue;
            }
            try {
                check.future().get(remaining, TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                unfinished.add(entry);
            } catch (CancellationException e) {
                // A concurrent refresh cancelled this check and scheduled a fresh one for the
                // same file. The mod is being re-checked, so it must not be marked unverified
                // here — that would report a healthy mod as unreadable.
            } catch (InterruptedException e) {
                // The launch thread was interrupted (e.g. the user cancelled). Restore the flag
                // so callers up the stack can observe it, and stop waiting.
                interrupted = true;
                unfinished.add(entry);
                break;
            } catch (Exception e) {
                // The check failed unexpectedly; the mod's state is unknown, not "sound"
                LOG.warning("Mod integrity check did not complete successfully", e);
                unfinished.add(entry);
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        lock.lock();
        try {
            if (!unfinished.isEmpty()) {
                // The result never arrived, so the mod's state is unknown. Marking it as
                // unverified is what keeps it from being presented to the user as sound.
                // Re-check that the entry is still the one currently registered for that file,
                // so a mod that a concurrent refresh already re-scheduled is left alone.
                int marked = 0;
                for (Map.Entry<Path, PendingCheck> entry : unfinished) {
                    if (pendingIntegrityChecks.get(entry.getKey()) == entry.getValue()) {
                        entry.getValue().modInfo().setIntegrityCheckFailed(true);
                        marked++;
                    }
                }
                if (marked > 0) {
                    LOG.warning("Did not receive " + marked + " mod integrity check result(s)");
                }
            }

            // Drop only the entries observed here that are still registered unchanged, so a
            // concurrent refresh's freshly scheduled checks are not swept away.
            for (Map.Entry<Path, PendingCheck> entry : checks) {
                pendingIntegrityChecks.remove(entry.getKey(), entry.getValue());
            }
        } finally {
            lock.unlock();
        }
    }

    public void addMod(Path file) throws IOException {
        if (!isFileNameMod(file))
            throw new IllegalArgumentException("File " + file + " is not a valid mod file.");

        lock.lock();
        try {
            if (!loaded)
                refresh();

            Path modsDirectory = getDirectory();
            Files.createDirectories(modsDirectory);

            Path newFile = modsDirectory.resolve(file.getFileName());
            FileUtils.copyFile(file, newFile);

            addModInfo(newFile, FileInfo.of(newFile));
        } finally {
            lock.unlock();
        }
    }

    public void removeMods(LocalModFile... localModFiles) throws IOException {
        for (LocalModFile localModFile : localModFiles) {
            localModFile.delete();
        }
    }

    private void updateSupportedLoaders() {
        supportedLoaders.clear();

        if (this.analyzer == null) {
            Collections.addAll(supportedLoaders, ModLoaderType.values());
            return;
        }

        for (GameComponentType type : GameComponentType.values()) {
            if (type.isModLoader() && this.analyzer.has(type)) {
                ModLoaderType modLoaderType = type.getModLoaderType();
                if (modLoaderType != null) {
                    supportedLoaders.add(modLoaderType);

                    if (modLoaderType == ModLoaderType.CLEANROOM)
                        supportedLoaders.add(ModLoaderType.FORGE);
                }
            }
        }

        if (this.analyzer.has(GameComponentType.NEO_FORGE) && "1.20.1".equals(gameVersion)) {
            supportedLoaders.add(ModLoaderType.FORGE);
        }

        if (this.analyzer.has(GameComponentType.QUILT)) {
            supportedLoaders.add(ModLoaderType.FABRIC);
        }

        if (this.analyzer.has(GameComponentType.LEGACY_FABRIC)) {
            supportedLoaders.add(ModLoaderType.FABRIC);
        }

        if (this.analyzer.has(GameComponentType.FABRIC) && hasMod("kilt", ModLoaderType.FABRIC)) {
            supportedLoaders.add(ModLoaderType.FORGE);
            supportedLoaders.add(ModLoaderType.NEO_FORGE);
        }

        // Sinytra Connector
        if (this.analyzer.has(GameComponentType.NEO_FORGE) && (hasMod("connector", ModLoaderType.NEO_FORGE) || hasMod("connectormod", ModLoaderType.NEO_FORGE))
                || "1.20.1".equals(gameVersion) && this.analyzer.has(GameComponentType.FORGE) && hasMod("connectormod", ModLoaderType.FORGE)) {
            supportedLoaders.add(ModLoaderType.FABRIC);
        }
    }

    public void rollback(LocalModFile from, LocalModFile to) throws IOException {
        lock.lock();
        try {
            if (!loaded) {
                throw new IllegalStateException("ModManager Not loaded");
            }
            if (!localFiles.contains(from)) {
                throw new IllegalStateException("Rolling back an unknown mod " + from.getFileName());
            }
            if (from.isOld()) {
                throw new IllegalArgumentException("Rolling back an old mod " + from.getFileName());
            }
            if (!to.isOld()) {
                throw new IllegalArgumentException("Rolling back to an old path " + to.getFileName());
            }
            if (from.getFileName().equals(to.getFileName())) {
                // We cannot roll back to the mod with the same name.
                return;
            }

            LocalMod mod = Objects.requireNonNull(from.getMod());
            if (mod != to.getMod()) {
                throw new IllegalArgumentException("Rolling back mod " + from.getFileName() + " to a different mod " + to.getFileName());
            }
            if (!mod.getFiles().contains(from)
                    || !mod.getOldFiles().contains(to)) {
                throw new IllegalStateException("LocalMod state corrupt");
            }

            boolean active = from.isActive();
            from.setActive(true);
            from.setOld(true);
            to.setOld(false);
            to.setActive(active);
        } finally {
            lock.unlock();
        }
    }

    public Path disableMod(Path file) throws IOException {
        if (isOld(file)) return file; // no need to disable an old mod.

        String fileName = FileUtils.getName(file);
        if (fileName.endsWith(DISABLED_EXTENSION)) return file;

        Path disabled = file.resolveSibling(fileName + DISABLED_EXTENSION);
        if (Files.exists(file))
            Files.move(file, disabled, StandardCopyOption.REPLACE_EXISTING);
        return disabled;
    }

    public Path enableMod(Path file) throws IOException {
        if (isOld(file)) return file;
        Path enabled = file.resolveSibling(StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION));
        if (Files.exists(file))
            Files.move(file, enabled, StandardCopyOption.REPLACE_EXISTING);
        return enabled;
    }

    public boolean isOld(Path file) {
        return FileUtils.getName(file).endsWith(OLD_EXTENSION);
    }

    public boolean isDisabled(Path file) {
        return FileUtils.getName(file).endsWith(DISABLED_EXTENSION);
    }

    public static boolean isFileNameMod(Path file) {
        String name = getLocalAddonName(file);
        return MOD_EXTENSIONS.contains(FileUtils.getExtension(name).toLowerCase(Locale.ROOT));
    }

    public static boolean isFileMod(Path modFile) {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(modFile)) {
            if (Files.exists(fs.getPath("mcmod.info")) || Files.exists(fs.getPath("META-INF/mods.toml"))) {
                // Forge mod
                return true;
            }

            if (Files.exists(fs.getPath("fabric.mod.json"))) {
                // Fabric mod
                return true;
            }

            if (Files.exists(fs.getPath("quilt.mod.json"))) {
                // Quilt mod
                return true;
            }

            if (Files.exists(fs.getPath("litemod.json"))) {
                // Liteloader mod
                return true;
            }

            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Check if "mods" directory has mod file named "fileName" no matter the mod is disabled, upgraded or not
     *
     * @param fileName name of the file whose existence is being checked
     * @return true if the file exists
     */
    public boolean hasSimpleMod(String fileName) {
        return Files.exists(getDirectory().resolve(StringUtils.removeSuffix(fileName, DISABLED_EXTENSION)))
                || Files.exists(getDirectory().resolve(StringUtils.addSuffix(fileName, DISABLED_EXTENSION)))
                || Files.exists(getDirectory().resolve(StringUtils.removeSuffix(fileName, OLD_EXTENSION)))
                || Files.exists(getDirectory().resolve(StringUtils.addSuffix(fileName, OLD_EXTENSION)));
    }

    public Path getSimpleModPath(String fileName) {
        return getDirectory().resolve(fileName);
    }
}
