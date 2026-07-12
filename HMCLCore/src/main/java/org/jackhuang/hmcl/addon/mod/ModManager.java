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
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ModManager extends LocalAddonManager<LocalModFile> {
    public static final List<String> MOD_EXTENSIONS = List.of("jar", "litemod");

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
        map.put("litemod", List.of(pair(LiteModMetadata::fromFile, ModLoaderType.LITE_LOADER)));

        READERS = map;
    }

    private final HashMap<Pair<String, ModLoaderType>, LocalMod> localMods = new HashMap<>();
    private LibraryAnalyzer analyzer;

    private boolean loaded = false;

    // Caches parsed mods by file path + fingerprint, so repeated refreshes only re-parse the
    // files that were actually added, removed, or changed instead of rescanning everything.
    private final Map<Path, CachedMod> cache = new HashMap<>();

    private record CachedMod(long lastModified, long size, LocalModFile mod) {
    }

    public ModManager(GameRepository repository, String id) {
        super(repository, id);
    }

    @Override
    public Path getDirectory() {
        return repository.getModsDirectory(id);
    }

    public LibraryAnalyzer getLibraryAnalyzer() {
        return analyzer;
    }

    /// The instance's Minecraft version, or {@code null} if it can't be resolved. Used to highlight
    /// which copy inside a multi-version Jar-in-Jar "wrapper" would actually be activated.
    public @org.jetbrains.annotations.Nullable String getGameVersion() {
        try {
            return repository.getGameVersion(id).orElse(null);
        } catch (Exception e) {
            return null;
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

    private LocalModFile addModInfo(Path file) {
        String fileName = StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION, OLD_EXTENSION);
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);

        List<Pair<ModMetadataReader, ModLoaderType>> readersMap = READERS.get(extension);
        if (readersMap == null) {
            // Is not a mod file.
            return null;
        }

        Set<ModLoaderType> modLoaderTypes = analyzer.getModLoaders();

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

    private void removeModInfo(LocalModFile modInfo) {
        localFiles.remove(modInfo);

        LocalMod mod = modInfo.getMod();
        mod.getFiles().remove(modInfo);
        mod.getOldFiles().remove(modInfo);
        if (mod.getFiles().isEmpty() && mod.getOldFiles().isEmpty()) {
            localMods.remove(pair(mod.getId(), mod.getModLoaderType()));
        }
    }

    private static boolean isModCandidate(Path file) {
        String name = StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION, OLD_EXTENSION);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && READERS.containsKey(name.substring(dot + 1));
    }

    private void collectModFiles(Path file, Map<Path, long[]> current) {
        if (!isModCandidate(file)) // pure string check first, so non-mod files cost no syscall
            return;
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (attributes.isRegularFile())
                current.put(file, new long[]{attributes.lastModifiedTime().toMillis(), attributes.size()});
        } catch (IOException e) {
            LOG.warning("Failed to stat mod file " + file, e);
        }
    }

    @Override
    public void refresh() throws IOException {
        lock.lock();
        try {
            analyzer = LibraryAnalyzer.analyze(getRepository().getResolvedPreservingPatchesVersion(id), null);

            boolean supportSubfolders = analyzer.has(LibraryAnalyzer.LibraryType.FORGE)
                    || analyzer.has(LibraryAnalyzer.LibraryType.QUILT);

            // Snapshot the current mod files on disk together with their fingerprints.
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

            // Drop cached mods whose file was removed or changed.
            Iterator<Map.Entry<Path, CachedMod>> iterator = cache.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Path, CachedMod> entry = iterator.next();
                long[] stamp = current.get(entry.getKey());
                CachedMod cached = entry.getValue();
                if (stamp == null || stamp[0] != cached.lastModified() || stamp[1] != cached.size()) {
                    removeModInfo(cached.mod());
                    iterator.remove();
                }
            }

            // Parse only the files that are new or changed; reuse the rest from the cache.
            for (Map.Entry<Path, long[]> entry : current.entrySet()) {
                if (cache.containsKey(entry.getKey()))
                    continue;
                LocalModFile modInfo = addModInfo(entry.getKey());
                if (modInfo != null) {
                    cache.put(entry.getKey(), new CachedMod(entry.getValue()[0], entry.getValue()[1], modInfo));
                }
            }

            loaded = true;
        } finally {
            lock.unlock();
        }
    }

    /// Resolves the full Jar-in-Jar tree for every loaded mod that declares nested jars but hasn't
    /// been deep-scanned yet, so the dependency cascade and the bundled-dependency report see the
    /// complete set of nested mod ids. Split out from {@link #refresh()} and meant to run on a
    /// background thread *after* the list is shown — reaching a nested jar means extracting it, too
    /// slow to block the first paint. Idempotent; the result is cached on each {@link LocalModFile}.
    // Serializes scanBundledTrees per on-disk cache file. Static and keyed by path because
    // DefaultGameRepository.getModManager() builds a fresh ModManager per call — the mod list page
    // and a crash-report export may scan the SAME instance through DIFFERENT ModManager objects
    // concurrently, and unserialized writers would corrupt the shared cache file.
    private static final Map<Path, ReentrantLock> JIJ_SCAN_LOCKS = new ConcurrentHashMap<>();

    public void scanBundledTrees() {
        List<CachedMod> snapshot;
        lock.lock();
        try {
            if (!loaded)
                return;
            snapshot = new ArrayList<>(cache.values());
        } finally {
            lock.unlock();
        }

        List<CachedMod> pending = new ArrayList<>();
        for (CachedMod cm : snapshot)
            if (cm.mod().hasBundledMods() && cm.mod().getBundledTree().isEmpty())
                pending.add(cm);
        if (pending.isEmpty())
            return;

        // Consult the on-disk cache so the expensive extract/scan only runs for mods whose fingerprint
        // changed since last time. This runs outside the main lock (the slow part must not block other
        // mod operations), but is serialized per cache file — see JIJ_SCAN_LOCKS.
        Path cacheFile = jijCacheFile();
        ReentrantLock scanLock = JIJ_SCAN_LOCKS.computeIfAbsent(cacheFile, k -> new ReentrantLock());
        scanLock.lock();
        try {
            Map<String, NestedJarCache.Entry> persisted = NestedJarCache.load(cacheFile);
            boolean dirty = false;
            for (CachedMod cm : pending) {
                if (!cm.mod().getBundledTree().isEmpty())
                    continue; // resolved by a concurrent scan while we waited for the lock
                String key = jijCacheKey(cm.mod().getFile());
                NestedJarCache.Entry hit = key == null ? null : persisted.get(key);
                if (hit != null && hit.lastModified() == cm.lastModified() && hit.size() == cm.size()) {
                    cm.mod().setBundledTree(hit.tree()); // fingerprint match — reuse, no extraction
                } else {
                    try (ZipFileTree tree = CompressingUtils.openZipTree(cm.mod().getFile())) {
                        cm.mod().setBundledTree(NestedJarInspector.scan(tree));
                        dirty = true;
                    } catch (Exception e) {
                        LOG.warning("Failed to scan Jar-in-Jar tree of " + cm.mod().getFile(), e);
                    }
                }
            }

            // A fresh scan happened — rewrite the cache from the current mods (dropping any now gone).
            if (dirty) {
                Map<String, NestedJarCache.Entry> fresh = new LinkedHashMap<>();
                for (CachedMod cm : snapshot) {
                    if (!cm.mod().hasBundledMods())
                        continue;
                    List<NestedJarInspector.NestedJar> tree = cm.mod().getBundledTree();
                    if (tree.isEmpty())
                        continue;
                    String key = jijCacheKey(cm.mod().getFile());
                    if (key != null)
                        fresh.put(key, new NestedJarCache.Entry(cm.lastModified(), cm.size(), tree));
                }
                NestedJarCache.save(cacheFile, fresh);
            }
        } finally {
            scanLock.unlock();
        }
    }

    private Path jijCacheFile() {
        // Under the instance's .hmcl/state — transient, rebuildable launcher data.
        return repository.getInstanceStateDirectory(id).resolve("jij-cache.json").toAbsolutePath().normalize();
    }

    /// The mod file keyed by its path relative to the mods directory (forward-slashed for a
    /// platform-independent key), or {@code null} if it is not under the mods directory. The
    /// {@code .disabled} suffix is stripped so toggling a mod on/off (a pure rename that keeps
    /// mtime+size) doesn't change its key and defeat the cache. {@code .old} is kept: an .old file
    /// can coexist with its replacement, and stripping it would collide their keys.
    private String jijCacheKey(Path file) {
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

            CachedMod previous = cache.remove(newFile);
            if (previous != null) {
                removeModInfo(previous.mod());
            }

            FileUtils.copyFile(file, newFile);

            LocalModFile modInfo = addModInfo(newFile);
            if (modInfo != null) {
                cache.put(newFile, new CachedMod(Files.getLastModifiedTime(newFile).toMillis(), Files.size(newFile), modInfo));
            }
        } finally {
            lock.unlock();
        }
    }

    public void removeMods(LocalModFile... localModFiles) throws IOException {
        for (LocalModFile localModFile : localModFiles) {
            localModFile.delete();
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
