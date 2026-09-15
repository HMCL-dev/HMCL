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
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ModManager extends LocalAddonManager<LocalModFile> {
    public static final List<String> MOD_EXTENSIONS = List.of("jar", "zip", "litemod");

    /// Number of candidate files below which parsing stays sequential.
    ///
    /// Spinning up parallel work for a handful of files costs more than it saves.
    private static final int PARALLEL_THRESHOLD = 8;

    @FunctionalInterface
    private interface ModMetadataReader {
        ModMetadata fromFile(Path modFile, ZipFileTree tree) throws IOException, JsonParseException;
    }

    /// Readers available for one file extension, split by whether the instance supports them.
    ///
    /// The split depends only on the extension and the instance's mod loader set, so it is computed
    /// once per refresh rather than once per file.
    ///
    /// @param supported   readers for mod loaders present in the instance, tried first
    /// @param unsupported readers for the remaining mod loaders, tried as a fallback
    private record ReaderPartition(List<ModMetadataReader> supported, List<ModMetadataReader> unsupported) {
    }

    /// A parsed mod file together with the path it was parsed from.
    ///
    /// @param file     the file the metadata was read from
    /// @param metadata the parsed metadata
    private record ParsedMod(Path file, ModMetadata metadata) {
    }

    /// Cached metadata of one file, keyed by the file state that produced it.
    ///
    /// @param size         the file size in bytes at parse time
    /// @param lastModified the last modified time in milliseconds at parse time
    /// @param metadata     the metadata parsed from that file state
    private record CacheEntry(long size, long lastModified, ModMetadata metadata) {
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

    private final HashMap<Pair<String, ModLoaderType>, LocalMod> localMods = new HashMap<>();

    /// Metadata parsed from every file seen by the last refresh, keyed by file path.
    ///
    /// Entries survive across refreshes and are re-parsed only when the file's size or last
    /// modified time changes, so refreshing an unchanged mods directory costs one `stat` per file
    /// instead of a full re-parse of every archive.
    private final ConcurrentHashMap<Path, CacheEntry> parseCache = new ConcurrentHashMap<>();

    private GameComponentAnalyzer analyzer;

    private boolean loaded = false;

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
        return analyzer;
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

    /// Builds the reader partition for every supported file extension.
    ///
    /// @param modLoaderTypes the mod loaders present in the instance
    /// @return a map from file extension to the readers that should be tried for it
    private static Map<String, ReaderPartition> buildReaderPartitions(Set<ModLoaderType> modLoaderTypes) {
        var result = new HashMap<String, ReaderPartition>();

        for (var entry : READERS.entrySet()) {
            var supported = new ArrayList<ModMetadataReader>();
            var unsupported = new ArrayList<ModMetadataReader>();

            for (Pair<ModMetadataReader, ModLoaderType> reader : entry.getValue()) {
                if (modLoaderTypes.contains(reader.getValue())) {
                    supported.add(reader.getKey());
                } else {
                    unsupported.add(reader.getKey());
                }
            }

            result.put(entry.getKey(), new ReaderPartition(List.copyOf(supported), List.copyOf(unsupported)));
        }

        return result;
    }

    /// Extracts the file extension used to look up readers.
    ///
    /// @param file the file to inspect
    /// @return the extension without the leading dot, or `null` when the name has no dot
    private static @Nullable String modExtensionOf(Path file) {
        String fileName = StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION, OLD_EXTENSION);
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? null : fileName.substring(dot + 1);
    }

    /// Lists the files under the mods directory that may carry mod metadata.
    ///
    /// Non-mod files are filtered out here so that the parse phase does not have to stat them.
    ///
    /// @param supportSubfolders whether mods may live in subdirectories of the mods directory
    /// @return the candidate mod files
    /// @throws IOException if the mods directory cannot be listed
    private List<Path> collectModFiles(boolean supportSubfolders) throws IOException {
        Path directory = getDirectory();
        if (!Files.isDirectory(directory))
            return List.of();

        var result = new ArrayList<Path>();
        try (DirectoryStream<Path> modsDirectoryStream = Files.newDirectoryStream(directory)) {
            for (Path subitem : modsDirectoryStream) {
                if (supportSubfolders && Files.isDirectory(subitem)
                        && !".connector".equalsIgnoreCase(subitem.getFileName().toString())) {
                    try (DirectoryStream<Path> subitemDirectoryStream = Files.newDirectoryStream(subitem)) {
                        for (Path subsubitem : subitemDirectoryStream) {
                            if (isModCandidate(subsubitem))
                                result.add(subsubitem);
                        }
                    }
                } else if (isModCandidate(subitem)) {
                    result.add(subitem);
                }
            }
        }
        return result;
    }

    /// Returns whether [file] has an extension some reader can handle.
    ///
    /// @param file the file to inspect
    /// @return true if the file may be a mod file
    private static boolean isModCandidate(Path file) {
        String extension = modExtensionOf(file);
        return extension != null && READERS.containsKey(extension);
    }

    /// Parses every candidate file, in parallel when there are enough of them.
    ///
    /// Parsing is free of shared state, so it is safe to run concurrently and outside the manager
    /// lock. Results are merged into the manager state afterwards, on a single thread.
    ///
    /// @param candidates the files to parse
    /// @param partitions the reader partitions for the current instance
    /// @return the parsed results, skipping files that could not be read
    private List<ParsedMod> parseAll(List<Path> candidates, Map<String, ReaderPartition> partitions) {
        if (candidates.size() < PARALLEL_THRESHOLD) {
            var result = new ArrayList<ParsedMod>(candidates.size());
            for (Path file : candidates) {
                ModMetadata metadata = parseCached(file, partitions);
                if (metadata != null)
                    result.add(new ParsedMod(file, metadata));
            }
            return result;
        }

        return candidates.parallelStream()
                .map(file -> {
                    ModMetadata metadata = parseCached(file, partitions);
                    return metadata != null ? new ParsedMod(file, metadata) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /// Returns the metadata of [file], reusing the cached result while the file is unchanged.
    ///
    /// @param file       the candidate mod file
    /// @param partitions the reader partitions for the current instance
    /// @return the parsed metadata, or `null` when the file is not a mod file or vanished
    private @Nullable ModMetadata parseCached(Path file, Map<String, ReaderPartition> partitions) {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(file, BasicFileAttributes.class);
        } catch (IOException e) {
            // The entry disappeared between listing the directory and reading it.
            return null;
        }

        if (!attributes.isRegularFile())
            return null;

        long size = attributes.size();
        long lastModified = attributes.lastModifiedTime().toMillis();

        CacheEntry cached = parseCache.get(file);
        if (cached != null && cached.size() == size && cached.lastModified() == lastModified)
            return cached.metadata();

        ModMetadata metadata = parseModFile(file, partitions);
        if (metadata == null)
            return null;

        parseCache.put(file, new CacheEntry(size, lastModified, metadata));
        return metadata;
    }

    /// Parses the metadata of one mod file without touching any manager state.
    ///
    /// This method is safe to call concurrently: it only reads the file and the concurrent
    /// [#parseCache], and returns a detached [ModMetadata]. Failures are logged and turned into the
    /// same fallback metadata the sequential implementation produced.
    ///
    /// @param file       the candidate mod file
    /// @param partitions the reader partitions for the current instance
    /// @return the parsed metadata, or `null` when no reader handles the file extension
    private @Nullable ModMetadata parseModFile(Path file, Map<String, ReaderPartition> partitions) {
        String extension = modExtensionOf(file);
        if (extension == null)
            return null;

        ReaderPartition partition = partitions.get(extension);
        if (partition == null) {
            // Is not a mod file.
            return null;
        }

        ModMetadata metadata = null;

        List<Exception> exceptions = new ArrayList<>();
        try (ZipFileTree tree = CompressingUtils.openZipTree(file)) {
            for (ModMetadataReader reader : partition.supported()) {
                try {
                    metadata = reader.fromFile(file, tree);
                    break;
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }

            if (metadata == null) {
                for (ModMetadataReader reader : partition.unsupported()) {
                    try {
                        metadata = reader.fromFile(file, tree);
                        break;
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            LOG.warning("Failed to open mod file " + file, e);
        }

        if (metadata != null)
            return metadata;

        Exception exception = new Exception("Failed to read mod metadata");
        for (Exception e : exceptions) {
            exception.addSuppressed(e);
        }
        LOG.warning("Failed to read mod metadata", exception);

        String fileNameWithoutExtension = FileUtils.getNameWithoutExtension(file);
        return new ModMetadata(fileNameWithoutExtension, ModLoaderType.UNKNOWN, fileNameWithoutExtension,
                new LocalAddonFile.Description("litemod".equals(extension) ? "LiteLoader Mod" : ""),
                "", "", "", "", "");
    }

    /// Drops cached entries for files that are no longer candidates.
    ///
    /// @param candidates the files collected by the current refresh
    private void pruneParseCache(List<Path> candidates) {
        parseCache.keySet().retainAll(new HashSet<>(candidates));
    }

    /// Creates the [LocalModFile] for [metadata] and registers it with the manager.
    ///
    /// The caller must hold [lock].
    ///
    /// @param file     the mod file the metadata was parsed from
    /// @param metadata the parsed metadata
    private void mergeModInfo(Path file, ModMetadata metadata) {
        LocalModFile modInfo = new LocalModFile(this,
                getLocalMod(metadata.modId(), metadata.loaderType()),
                file,
                metadata.name(),
                metadata.description(),
                metadata.authors(),
                metadata.version(),
                metadata.gameVersion(),
                metadata.url(),
                metadata.logoPath());

        if (!modInfo.isOld()) {
            addLocalFile(modInfo);
        }
    }

    /// Parses [file] and merges the result into the manager state.
    ///
    /// The caller must hold [lock].
    ///
    /// @param file the mod file to add
    private void addModInfo(Path file) {
        ModMetadata metadata = parseCached(file, buildReaderPartitions(instance.getModLoaders()));
        if (metadata != null)
            mergeModInfo(file, metadata);
    }

    @Override
    public void refresh() throws IOException {
        // The whole scan runs outside the manager lock so that concurrent readers are not blocked
        // while the mods directory is being read.
        GameComponentAnalyzer analyzer = instance.getAnalyzer();
        Map<String, ReaderPartition> partitions = buildReaderPartitions(instance.getModLoaders());

        boolean supportSubfolders = analyzer.has(GameComponentType.FORGE)
                || analyzer.has(GameComponentType.QUILT)
                || analyzer.has(GameComponentType.CLEANROOM)
                || analyzer.has(GameComponentType.LITELOADER);

        List<Path> candidates = collectModFiles(supportSubfolders);

        List<ParsedMod> parsed = parseAll(candidates, partitions);

        // Only the merge below mutates shared state, and it is cheap: it allocates one LocalModFile
        // per parsed file and inserts it into the manager's collections.
        lock.lock();
        try {
            clearLocalFiles();
            localMods.clear();

            this.analyzer = analyzer;

            pruneParseCache(candidates);

            for (ParsedMod mod : parsed) {
                mergeModInfo(mod.file(), mod.metadata());
            }

            loaded = true;
        } finally {
            lock.unlock();
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
            FileUtils.copyFile(file, newFile);

            addModInfo(newFile);
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
