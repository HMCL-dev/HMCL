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
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

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

    private void addModInfo(Path file) {
        String fileName = StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION, OLD_EXTENSION);
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);

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
            localFiles.clear();
            localMods.clear();

            // Re-read the pin file as well, so edits made outside this manager are picked up.
            pinnedFiles = null;

            analyze();

            boolean supportSubfolders = analyzer.has(GameComponentType.FORGE)
                    || analyzer.has(GameComponentType.QUILT)
                    || analyzer.has(GameComponentType.CLEANROOM)
                    || analyzer.has(GameComponentType.LITELOADER);

            if (Files.isDirectory(getDirectory())) {
                try (DirectoryStream<Path> modsDirectoryStream = Files.newDirectoryStream(getDirectory())) {
                    for (Path subitem : modsDirectoryStream) {
                        if (supportSubfolders && Files.isDirectory(subitem) && !".connector".equalsIgnoreCase(subitem.getFileName().toString())) {
                            try (DirectoryStream<Path> subitemDirectoryStream = Files.newDirectoryStream(subitem)) {
                                for (Path subsubitem : subitemDirectoryStream) {
                                    addModInfo(subsubitem);
                                }
                            }
                        } else {
                            addModInfo(subitem);
                        }
                    }
                }
            }

            updateSupportedLoaders();

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

    /// Name of the file in the mods directory that lists the mods pinned to their current version, one per line.
    ///
    /// The file has no mod extension, so [#refresh()] skips it.
    public static final String PINNED_FILE_NAME = ".hmcl-pinned";

    /// Pinned mod file names, or null until they are read from [#PINNED_FILE_NAME].
    private @Nullable Set<String> pinnedFiles;

    /// Returns whether the mod at the given path stays at its current version during update checks.
    ///
    /// @param file the mod file
    /// @return true if the mod should not be updated
    public boolean isPinned(Path file) {
        lock.lock();
        try {
            return getPinnedFiles().contains(getLocalAddonName(file));
        } finally {
            lock.unlock();
        }
    }

    /// Pins or unpins the given mods, writing [#PINNED_FILE_NAME] at most once.
    ///
    /// A name is stored without the `.disabled` or `.old` suffix, so pinning survives enabling,
    /// disabling and rolling back the mod.
    ///
    /// @param files  the mod files to change
    /// @param pinned whether they should stay at their current versions
    /// @throws IOException if the pin file cannot be written
    public void setPinned(Collection<Path> files, boolean pinned) throws IOException {
        lock.lock();
        try {
            var names = new HashSet<>(getPinnedFiles());
            boolean changed = false;
            for (Path file : files) {
                if (pinned ? names.add(getLocalAddonName(file)) : names.remove(getLocalAddonName(file))) {
                    changed = true;
                }
            }

            if (changed) {
                pinnedFiles = Set.copyOf(names);
                savePinnedFiles(pinnedFiles);
            }
        } finally {
            lock.unlock();
        }
    }

    /// Returns the pinned mod names, reading [#PINNED_FILE_NAME] on first access.
    ///
    /// @return the pinned mod file names, empty when nothing is pinned
    private @Unmodifiable Set<String> getPinnedFiles() {
        if (pinnedFiles == null) {
            pinnedFiles = loadPinnedFiles();
        }
        return pinnedFiles;
    }

    /// Reads [#PINNED_FILE_NAME] from the mods directory.
    ///
    /// A missing or unreadable file yields an empty set, so it never hides the mod list.
    ///
    /// @return the pinned mod file names
    private @Unmodifiable Set<String> loadPinnedFiles() {
        Path file = getDirectory().resolve(PINNED_FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Set.of();
        }

        try {
            Set<String> names = new HashSet<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String name = line.trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
            return Set.copyOf(names);
        } catch (IOException e) {
            LOG.warning("Unable to read pinned mods from " + file, e);
            return Set.of();
        }
    }

    /// Writes the pinned mod names to [#PINNED_FILE_NAME], sorted to keep the file stable.
    ///
    /// @param names the pinned mod file names
    /// @throws IOException if the file cannot be written
    private void savePinnedFiles(Collection<String> names) throws IOException {
        Files.write(getDirectory().resolve(PINNED_FILE_NAME), names.stream().sorted().toList(), StandardCharsets.UTF_8);
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
