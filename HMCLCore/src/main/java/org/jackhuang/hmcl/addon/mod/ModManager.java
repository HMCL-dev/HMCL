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
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.meta.*;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.NoSuchGameInstanceException;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CancellationException;

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

    /// Loader set used when the in-memory parse cache was built.
    private @Nullable Set<ModLoaderType> cachedModLoaders;

    /// Parsed mod files indexed by path and guarded by [#lock].
    private final Map<Path, CachedMod> cache = new HashMap<>();

    /// Files whose JIJ scan failed for a non-transient reason during this manager's lifetime.
    /// Access is serialized by the manager's single [#lock].
    private final Set<Path> jijScanFailed = new HashSet<>();

    /// Files whose nested-jar declarations were completely checked, including files with no JIJ.
    private final Set<Path> jijScanCompleted = new HashSet<>();

    /// Immutable relation index for the latest completed top-level and JIJ scan.
    private @Nullable ModRelationIndex relationIndex;

    /// Remote project identities cached by source and local file fingerprint.
    private final Map<RemoteIdentityKey, Optional<RemoteAddon.Version>> remoteIdentityCache = new HashMap<>();

    /// Associates a parsed mod with the filesystem fingerprint from its last refresh.
    private record CachedMod(long lastModified, long size, LocalModFile mod) {
    }

    /// Cache key for one source-specific remote identity lookup.
    private record RemoteIdentityKey(
            RemoteAddon.Source source,
            Path path,
            long lastModified,
            long size) {
    }

    /// Creates a mod manager for the given instance.
    ///
    /// @param instance the snapshot member whose mods directory this manager operates on
    public ModManager(DefaultGameInstance instance) {
        super(instance);
    }

    /// Rebinds the stable manager and invalidates analysis when instance layout or manifest changes.
    @Override
    public void rebindInstance(DefaultGameInstance instance) {
        lock.lock();
        try {
            if (this.instance == instance) {
                return;
            }
            boolean changed = !this.instance.getManifest().equals(instance.getManifest())
                    || !this.instance.getModsDirectory().equals(instance.getModsDirectory());
            super.rebindInstance(instance);
            if (changed) {
                loaded = false;
                relationIndex = null;
            }
        } finally {
            lock.unlock();
        }
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

    /// Parses one candidate mod file and adds it to the manager indexes.
    private @Nullable LocalModFile addModInfo(Path file) {
        String fileName = StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION, OLD_EXTENSION);
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);

        List<Pair<ModMetadataReader, ModLoaderType>> readersMap = READERS.get(extension);
        if (readersMap == null) {
            // Is not a mod file.
            return null;
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

        if (modInfo == null) {
            Exception exception = new Exception("Failed to read mod metadata");
            for (Exception e : exceptions) {
                exception.addSuppressed(e);
            }
            LOG.warning("Failed to read mod metadata", exception);

            String fileNameWithoutExtension = FileUtils.getNameWithoutExtension(file);

            modInfo = new LocalModFile(this,
                    getLocalMod(fileNameWithoutExtension, ModLoaderType.UNKNOWN),
                    file,
                    fileNameWithoutExtension,
                    new LocalAddonFile.Description("litemod".equals(extension) ? "LiteLoader Mod" : "")
            );
        }

        if (!modInfo.isOld()) {
            localFiles.add(modInfo);
        }

        return modInfo;
    }

    /// Removes a parsed file from both the flat file list and its logical mod grouping.
    private void removeModInfo(LocalModFile modInfo) {
        localFiles.remove(modInfo);

        LocalMod mod = modInfo.getMod();
        mod.getFiles().remove(modInfo);
        mod.getOldFiles().remove(modInfo);
        if (mod.getFiles().isEmpty() && mod.getOldFiles().isEmpty()) {
            localMods.remove(pair(mod.getId(), mod.getModLoaderType()));
        }
    }

    /// Returns whether the path name can represent a supported mod archive.
    private static boolean isModCandidate(Path file) {
        String name = StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION, OLD_EXTENSION);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && READERS.containsKey(name.substring(dot + 1));
    }

    /// Adds a regular candidate file and its fingerprint to a refresh snapshot.
    private void collectModFiles(Path file, Map<Path, long[]> current) {
        if (!isModCandidate(file)) {
            return;
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (attributes.isRegularFile()) {
                current.put(file, new long[]{attributes.lastModifiedTime().toMillis(), attributes.size()});
            }
        } catch (IOException e) {
            LOG.warning("Failed to stat mod file " + file, e);
        }
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
            relationIndex = null;
            jijScanFailed.clear();
            analyze();

            boolean supportSubfolders = analyzer.has(GameComponentType.FORGE)
                    || analyzer.has(GameComponentType.QUILT)
                    || analyzer.has(GameComponentType.CLEANROOM)
                    || analyzer.has(GameComponentType.LITELOADER);

            Set<ModLoaderType> modLoaders = instance.getModLoaders();
            if (!modLoaders.equals(cachedModLoaders)) {
                for (CachedMod cached : cache.values()) {
                    removeModInfo(cached.mod());
                }
                cache.clear();
                jijScanFailed.clear();
                jijScanCompleted.clear();
                cachedModLoaders = Set.copyOf(modLoaders);
            }

            Map<Path, long[]> current = new LinkedHashMap<>();
            if (Files.isDirectory(getDirectory())) {
                try (DirectoryStream<Path> modsDirectoryStream = Files.newDirectoryStream(getDirectory())) {
                    for (Path subitem : modsDirectoryStream) {
                        if (supportSubfolders && Files.isDirectory(subitem) && !".connector".equalsIgnoreCase(subitem.getFileName().toString())) {
                            try (DirectoryStream<Path> subitemDirectoryStream = Files.newDirectoryStream(subitem)) {
                                for (Path subsubitem : subitemDirectoryStream) {
                                    collectModFiles(subsubitem, current);
                                }
                            }
                        } else {
                            collectModFiles(subitem, current);
                        }
                    }
                }
            }

            Iterator<Map.Entry<Path, CachedMod>> iterator = cache.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Path, CachedMod> entry = iterator.next();
                long[] stamp = current.get(entry.getKey());
                CachedMod cached = entry.getValue();
                if (stamp == null || stamp[0] != cached.lastModified() || stamp[1] != cached.size()) {
                    removeModInfo(cached.mod());
                    jijScanFailed.remove(entry.getKey());
                    jijScanCompleted.remove(entry.getKey());
                    iterator.remove();
                }
            }

            for (Map.Entry<Path, long[]> entry : current.entrySet()) {
                if (cache.containsKey(entry.getKey())) {
                    continue;
                }
                @Nullable LocalModFile modInfo = addModInfo(entry.getKey());
                if (modInfo != null) {
                    jijScanFailed.remove(entry.getKey());
                    jijScanCompleted.remove(entry.getKey());
                    cache.put(entry.getKey(), new CachedMod(
                            entry.getValue()[0], entry.getValue()[1], modInfo));
                }
            }

            updateSupportedLoaders();
            remoteIdentityCache.keySet().removeIf(key -> !current.containsKey(key.path()));

            loaded = true;
        } finally {
            lock.unlock();
        }
    }

    /// Resolves complete Jar-in-Jar trees for loaded mods and persists reusable scan results.
    ///
    /// This stable repository-scoped manager is the sole writer for its instance cache. The manager
    /// lock serializes refresh, file mutations, deep scanning, and cache publication.
    private boolean scanBundledTrees(NestedJarInspector.ScanContext scanContext) {
        lock.lock();
        try {
            if (scanContext.isCancelled()) {
                throw new CancellationException("Jar-in-Jar scan cancelled");
            }
            if (!loaded) {
                return false;
            }

            List<CachedMod> snapshot = new ArrayList<>(cache.values());
            List<CachedMod> pending = new ArrayList<>();
            for (CachedMod cached : snapshot) {
                if (!jijScanCompleted.contains(cached.mod().getFile())
                        && !jijScanFailed.contains(cached.mod().getFile())) {
                    pending.add(cached);
                }
            }
            Path cacheFile = jijCacheFile();
            Map<String, NestedJarCache.Entry> persisted = NestedJarCache.load(cacheFile);
            boolean dirty = false;
            boolean incomplete = false;
            Set<String> truncatedKeys = new HashSet<>();
            for (CachedMod cached : pending) {
                if (scanContext.isCancelled()) {
                    throw new CancellationException("Jar-in-Jar scan cancelled");
                }
                if (!fingerprintMatches(cached)) {
                    loaded = false;
                    relationIndex = null;
                    return false;
                }
                @Nullable String key = jijCacheKey(cached.mod().getFile());
                @Nullable NestedJarCache.Entry hit = key == null ? null : persisted.get(key);
                if (hit != null
                        && hit.lastModified() == cached.lastModified()
                        && hit.size() == cached.size()
                        && hit.loaderKey().equals(loaderCacheKey())) {
                    cached.mod().setBundledTree(hit.tree());
                    jijScanCompleted.add(cached.mod().getFile());
                    continue;
                }

                try (ZipFileTree tree = CompressingUtils.openZipTree(cached.mod().getFile())) {
                    NestedJarInspector.ScanResult scanResult = NestedJarInspector.scan(
                            tree, Objects.requireNonNullElse(cachedModLoaders, Set.of()), scanContext);
                    if (!fingerprintMatches(cached)) {
                        loaded = false;
                        relationIndex = null;
                        return false;
                    }
                    cached.mod().setBundledTree(scanResult.tree());
                    if (scanResult.truncated()) {
                        if (key != null) {
                            truncatedKeys.add(key);
                        }
                        incomplete = true;
                    } else {
                        jijScanCompleted.add(cached.mod().getFile());
                    }
                    dirty = true;
                } catch (CancellationException e) {
                    throw e;
                } catch (Exception e) {
                    if (e instanceof NoSuchFileException || e instanceof FileNotFoundException) {
                        loaded = false;
                        relationIndex = null;
                        return false;
                    }
                    jijScanFailed.add(cached.mod().getFile());
                    incomplete = true;
                    LOG.warning("Failed to scan Jar-in-Jar tree of " + cached.mod().getFile(), e);
                }
            }

            Map<String, NestedJarCache.Entry> fresh = new LinkedHashMap<>(persisted);
            for (CachedMod cached : snapshot) {
                List<NestedJarInspector.NestedJar> tree = cached.mod().getBundledTree();
                if (!jijScanCompleted.contains(cached.mod().getFile())) {
                    continue;
                }
                @Nullable String key = jijCacheKey(cached.mod().getFile());
                if (key != null) {
                    fresh.put(key, new NestedJarCache.Entry(
                            cached.lastModified(), cached.size(), loaderCacheKey(), tree));
                }
            }
            fresh.keySet().removeAll(truncatedKeys);
            fresh.keySet().removeIf(key -> {
                Path modFile = getDirectory().resolve(key);
                return !Files.exists(modFile)
                        && !Files.exists(modFile.resolveSibling(modFile.getFileName() + DISABLED_EXTENSION));
            });
            if (dirty || !fresh.equals(persisted)) {
                NestedJarCache.save(cacheFile, fresh);
            }
            incomplete |= snapshot.stream()
                    .map(cached -> cached.mod().getFile())
                    .anyMatch(jijScanFailed::contains);
            relationIndex = null;
            return !incomplete;
        } finally {
            lock.unlock();
        }
    }

    /// Returns an immutable dependency/provider index for the latest complete scan.
    ///
    /// The first call refreshes top-level metadata when needed and completes the JIJ scan. Later
    /// callers, including UI and crash export, share the same manager and immutable index.
    ///
    /// @return the current relation index
    /// @throws IOException if top-level mod metadata cannot be refreshed
    public ModRelationIndex getRelationIndex() throws IOException {
        return getRelationIndex(new NestedJarInspector.ScanContext());
    }

    /// Returns the immutable relation index using a caller-owned cancellable scan context.
    ///
    /// @param scanContext shared cancellation and byte budget for this analysis request
    /// @return the current relation index
    /// @throws IOException if top-level mod metadata cannot be refreshed
    /// @throws CancellationException if the caller cancels the scan
    public ModRelationIndex getRelationIndex(NestedJarInspector.ScanContext scanContext) throws IOException {
        lock.lock();
        try {
            if (!loaded) {
                refresh();
            }
            if (relationIndex == null) {
                boolean complete = scanBundledTrees(scanContext);
                if (!complete) {
                    refresh();
                    complete = scanBundledTrees(scanContext);
                }
                relationIndex = new ModRelationIndex(
                        localFiles, gameVersion, supportedLoaders, complete);
            }
            return relationIndex;
        } finally {
            lock.unlock();
        }
    }

    /// Returns whether a cached file still has the fingerprint captured by the latest refresh.
    private static boolean fingerprintMatches(CachedMod cached) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    cached.mod().getFile(), BasicFileAttributes.class);
            return attributes.isRegularFile()
                    && attributes.lastModifiedTime().toMillis() == cached.lastModified()
                    && attributes.size() == cached.size();
        } catch (IOException e) {
            return false;
        }
    }

    /// Returns a stable cache discriminator for the loader-aware metadata reader preference.
    private String loaderCacheKey() {
        return Objects.requireNonNullElse(cachedModLoaders, Set.<ModLoaderType>of()).stream()
                .map(Enum::name)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    /// Invalidates relation and remote-identity projections after an enable/disable path mutation.
    void invalidatePublishedAnalysis() {
        lock.lock();
        try {
            relationIndex = null;
            remoteIdentityCache.clear();
        } finally {
            lock.unlock();
        }
    }

    /// Resolves and caches a local file's stable remote project/version identity for one source.
    ///
    /// @param source the remote platform
    /// @param file the parsed local file
    /// @return the matching remote version, or empty when the platform has no exact file match
    /// @throws IOException if the platform lookup fails
    public Optional<RemoteAddon.Version> resolveRemoteVersion(
            RemoteAddon.Source source,
            LocalModFile file) throws IOException {
        Path path = file.getFile();
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        Path normalized = path.toAbsolutePath().normalize();
        RemoteIdentityKey key = new RemoteIdentityKey(
                source,
                normalized,
                attributes.lastModifiedTime().toMillis(),
                attributes.size());

        lock.lock();
        try {
            @Nullable Optional<RemoteAddon.Version> cached = remoteIdentityCache.get(key);
            if (cached != null) {
                return cached;
            }
        } finally {
            lock.unlock();
        }

        RemoteAddonRepository repository = source.getRepository();
        Optional<RemoteAddon.Version> resolved = repository.getRemoteVersionByLocalFile(path);

        // Network I/O runs outside the manager lock. Publish only if the file identity is still the
        // same; a concurrent enable/disable or replacement will be looked up under its new stamp.
        BasicFileAttributes current = Files.readAttributes(file.getFile(), BasicFileAttributes.class);
        boolean unchanged = file.getFile().toAbsolutePath().normalize().equals(normalized)
                && current.lastModifiedTime().toMillis() == key.lastModified()
                && current.size() == key.size();
        if (unchanged) {
            lock.lock();
            try {
                remoteIdentityCache.keySet().removeIf(existing -> existing.source() == source
                        && existing.path().equals(normalized));
                remoteIdentityCache.put(key, resolved);
            } finally {
                lock.unlock();
            }
        }
        return resolved;
    }

    /// Returns the instance-local file that stores persisted JIJ scan results.
    private Path jijCacheFile() {
        return instance.getRepository().getLayout().getInstanceStateDirectory(instance.getId())
                .resolve("jij-cache.json")
                .toAbsolutePath()
                .normalize();
    }

    /// Returns a stable cache key relative to the mods directory.
    ///
    /// The disabled suffix is ignored so toggling a mod does not invalidate an otherwise matching
    /// fingerprint. Files outside the mods directory do not receive a cache key.
    private @Nullable String jijCacheKey(Path file) {
        try {
            String key = getDirectory().relativize(file).toString().replace('\\', '/');
            return StringUtils.removeSuffix(key, DISABLED_EXTENSION);
        } catch (IllegalArgumentException e) {
            return null;
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
            @Nullable CachedMod previous = cache.remove(newFile);
            if (previous != null) {
                removeModInfo(previous.mod());
            }

            FileUtils.copyFile(file, newFile);

            @Nullable LocalModFile modInfo = addModInfo(newFile);
            if (modInfo != null) {
                cache.put(newFile, new CachedMod(
                        Files.getLastModifiedTime(newFile).toMillis(),
                        Files.size(newFile),
                        modInfo));
            }
            relationIndex = null;
            remoteIdentityCache.clear();
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

    /// Returns whether a metadata ID is a known unexpanded template placeholder.
    ///
    /// Only exact placeholder values are rejected; valid IDs that merely contain `name` or `modid`
    /// remain usable.
    public static boolean isPlaceholderModId(@Nullable String id) {
        return id != null && (id.equalsIgnoreCase("name") || id.equalsIgnoreCase("modid"));
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
