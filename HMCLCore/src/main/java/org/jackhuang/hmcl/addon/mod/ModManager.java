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

    @FunctionalInterface
    private interface ModMetadataReader {
        LocalModFile.Metadata fromFile(Path modFile, ZipFileTree tree) throws IOException, JsonParseException;
    }

    private record ParsedMod(Path file, LocalModFile.Metadata metadata) {
    }

    private record CacheEntry(long size, long lastModified, LocalModFile.Metadata metadata) {
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

    /// Metadata of every file seen by the last refresh, keyed by file. An entry is reused while the
    /// file keeps its size and modification time, so refreshing an unchanged mods directory reads
    /// no archive at all.
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

    /// Returns the extension a file is looked up by, or an empty string when its name has none.
    private static String modExtensionOf(Path file) {
        return FileUtils.getExtension(getLocalAddonName(file));
    }

    private List<Path> collectCandidates(boolean supportSubfolders) throws IOException {
        Path directory = getDirectory();
        if (!Files.isDirectory(directory))
            return List.of();

        var result = new ArrayList<Path>();
        try (DirectoryStream<Path> modsDirectoryStream = Files.newDirectoryStream(directory)) {
            for (Path subitem : modsDirectoryStream) {
                if (supportSubfolders && Files.isDirectory(subitem) && !".connector".equalsIgnoreCase(subitem.getFileName().toString())) {
                    try (DirectoryStream<Path> subitemDirectoryStream = Files.newDirectoryStream(subitem)) {
                        for (Path subsubitem : subitemDirectoryStream) {
                            result.add(subsubitem);
                        }
                    }
                } else {
                    result.add(subitem);
                }
            }
        }
        return result;
    }

    private List<ParsedMod> parseAll(List<Path> candidates, Set<ModLoaderType> modLoaderTypes) {
        var result = new ArrayList<ParsedMod>(candidates.size());
        for (Path file : candidates) {
            LocalModFile.Metadata metadata = parseFile(file, modLoaderTypes);
            if (metadata != null)
                result.add(new ParsedMod(file, metadata));
        }
        return result;
    }

    /// Parses one candidate file, or returns `null` when it is not a mod.
    private @Nullable LocalModFile.Metadata parseFile(Path file, Set<ModLoaderType> modLoaderTypes) {
        String extension = modExtensionOf(file);
        List<Pair<ModMetadataReader, ModLoaderType>> readers = READERS.get(extension);
        if (readers == null) {
            // Is not a mod file.
            return null;
        }

        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(file, BasicFileAttributes.class);
        } catch (IOException e) {
            // The file disappeared between listing the directory and reading it.
            return null;
        }

        if (!attributes.isRegularFile())
            return null;

        long size = attributes.size();
        long lastModified = attributes.lastModifiedTime().toMillis();

        CacheEntry cached = parseCache.get(file);
        if (cached != null && cached.size() == size && cached.lastModified() == lastModified)
            return cached.metadata();

        LocalModFile.Metadata metadata = readMetadata(file, readers, modLoaderTypes);
        if (metadata == null) {
            // A file that could not be read still shows up in the list as an unrecognized mod, but
            // its state is not cached: the failure may be transient, so a refresh should retry it.
            String fileNameWithoutExtension = FileUtils.getNameWithoutExtension(file);
            return new LocalModFile.Metadata(fileNameWithoutExtension, ModLoaderType.UNKNOWN, fileNameWithoutExtension,
                    "litemod".equals(extension) ? "LiteLoader Mod" : "",
                    "", "", "", "", "");
        }

        parseCache.put(file, new CacheEntry(size, lastModified, metadata));
        return metadata;
    }

    /// Parses the metadata of one mod file, or returns `null` when no reader understood it.
    private static @Nullable LocalModFile.Metadata readMetadata(
            Path file,
            List<Pair<ModMetadataReader, ModLoaderType>> readers,
            Set<ModLoaderType> modLoaderTypes) {
        var supportedReaders = new ArrayList<ModMetadataReader>();
        var unsupportedReaders = new ArrayList<ModMetadataReader>();
        for (Pair<ModMetadataReader, ModLoaderType> reader : readers) {
            if (modLoaderTypes.contains(reader.getValue())) {
                supportedReaders.add(reader.getKey());
            } else {
                unsupportedReaders.add(reader.getKey());
            }
        }

        LocalModFile.Metadata metadata = null;

        List<Exception> exceptions = new ArrayList<>();
        try (ZipFileTree tree = CompressingUtils.openZipTree(file)) {
            for (ModMetadataReader reader : supportedReaders) {
                try {
                    metadata = reader.fromFile(file, tree);
                    break;
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }

            if (metadata == null) {
                for (ModMetadataReader reader : unsupportedReaders) {
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
        return null;
    }

    private void addModInfo(Path file, LocalModFile.Metadata metadata) {
        LocalModFile modInfo = new LocalModFile(this,
                getLocalMod(metadata.modId(), metadata.loaderType()),
                file,
                metadata.name(),
                // Readers take the description from JSON, where it may be absent or null; Description rejects null.
                new LocalAddonFile.Description(Objects.requireNonNullElse(metadata.description(), "")),
                metadata.authors(),
                metadata.version(),
                metadata.gameVersion(),
                metadata.url(),
                metadata.logoPath());

        if (!modInfo.isOld()) {
            localFiles.add(modInfo);
        }
    }

    @Override
    public void refresh() throws IOException {
        lock.lock();
        try {
            GameComponentAnalyzer analyzer = instance.getAnalyzer();

            boolean supportSubfolders = analyzer.has(GameComponentType.FORGE)
                    || analyzer.has(GameComponentType.QUILT)
                    || analyzer.has(GameComponentType.CLEANROOM)
                    || analyzer.has(GameComponentType.LITELOADER);

            List<Path> candidates = collectCandidates(supportSubfolders);
            List<ParsedMod> parsed = parseAll(candidates, instance.getModLoaders());

            localFiles.clear();
            localMods.clear();
            parseCache.keySet().retainAll(new HashSet<>(candidates));

            this.analyzer = analyzer;

            for (ParsedMod mod : parsed) {
                addModInfo(mod.file(), mod.metadata());
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

            LocalModFile.Metadata metadata = parseFile(newFile, instance.getModLoaders());
            if (metadata != null) {
                addModInfo(newFile, metadata);
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
