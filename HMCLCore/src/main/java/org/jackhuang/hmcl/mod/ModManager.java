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
package org.jackhuang.hmcl.mod;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.mod.modinfo.*;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ModManager {
    @FunctionalInterface
    private interface ModMetadataReader {
        LocalModFile fromFile(ModManager modManager, Path modFile, ZipFileTree tree) throws IOException, JsonParseException;
    }

    private static final Map<String, List<Pair<ModMetadataReader, ModLoaderType>>> READERS;

    static {
        var map = new HashMap<String, List<Pair<ModMetadataReader, ModLoaderType>>>();
        var zipReaders = List.<Pair<ModMetadataReader, ModLoaderType>>of(
                pair(ForgeNewModMetadata::fromForgeFile, ModLoaderType.FORGE),
                pair(ForgeNewModMetadata::fromNeoForgeFile, ModLoaderType.NEO_FORGED),
                pair(ForgeOldModMetadata::fromFile, ModLoaderType.FORGE),
                pair(FabricModMetadata::fromFile, ModLoaderType.FABRIC),
                pair(QuiltModMetadata::fromFile, ModLoaderType.QUILT),
                pair(PackMcMeta::fromFile, ModLoaderType.PACK)
        );

        map.put("zip", zipReaders);
        map.put("jar", zipReaders);
        map.put("litemod", List.of(pair(LiteModMetadata::fromFile, ModLoaderType.LITE_LOADER)));

        READERS = map;
    }

    private final GameRepository repository;
    private final String id;
    private final TreeSet<LocalModFile> localModFiles = new TreeSet<>();
    private final HashMap<Pair<String, ModLoaderType>, LocalMod> localMods = new HashMap<>();
    private LibraryAnalyzer analyzer;

    private boolean loaded = false;

    public ModManager(GameRepository repository, String id) {
        this.repository = repository;
        this.id = id;
    }

    public GameRepository getRepository() {
        return repository;
    }

    public String getInstanceId() {
        return id;
    }

    public Path getModsDirectory() {
        return repository.getModsDirectory(id);
    }

    public LibraryAnalyzer getLibraryAnalyzer() {
        return analyzer;
    }

    public LocalMod getLocalMod(String modId, ModLoaderType modLoaderType) {
        return localMods.computeIfAbsent(pair(modId, modLoaderType),
                x -> new LocalMod(x.getKey(), x.getValue()));
    }

    public boolean hasMod(String modId, ModLoaderType modLoaderType) {
        return localMods.containsKey(pair(modId, modLoaderType));
    }

    private void addModInfo(Path file) {
        String fileName = StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION, OLD_EXTENSION);
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);

        List<Pair<ModMetadataReader, ModLoaderType>> readersMap = READERS.get(extension);
        if (readersMap == null) {
            // Is not a mod file.
            return;
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
                    new LocalModFile.Description("litemod".equals(extension) ? "LiteLoader Mod" : "")
            );
        }

        if (!modInfo.isOld()) {
            localModFiles.add(modInfo);
        }
    }

    public void refreshMods() throws IOException {
        localModFiles.clear();
        localMods.clear();

        analyzer = LibraryAnalyzer.analyze(getRepository().getResolvedPreservingPatchesVersion(id), null);

        boolean supportSubfolders = analyzer.has(LibraryAnalyzer.LibraryType.FORGE)
                || analyzer.has(LibraryAnalyzer.LibraryType.QUILT);

        if (Files.isDirectory(getModsDirectory())) {
            try (DirectoryStream<Path> modsDirectoryStream = Files.newDirectoryStream(getModsDirectory())) {
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
        loaded = true;
    }

    public @Unmodifiable List<LocalModFile> getMods() throws IOException {
        if (!loaded)
            refreshMods();
        return List.copyOf(localModFiles);
    }

    public void addMod(Path file) throws IOException {
        if (!isFileNameMod(file))
            throw new IllegalArgumentException("File " + file + " is not a valid mod file.");

        if (!loaded)
            refreshMods();

        Path modsDirectory = getModsDirectory();
        Files.createDirectories(modsDirectory);

        Path newFile = modsDirectory.resolve(file.getFileName());
        FileUtils.copyFile(file, newFile);

        addModInfo(newFile);
    }

    public void removeMods(LocalModFile... localModFiles) throws IOException {
        for (LocalModFile localModFile : localModFiles) {
            Files.deleteIfExists(localModFile.getFile());
        }
    }

    public void rollback(LocalModFile from, LocalModFile to) throws IOException {
        if (!loaded) {
            throw new IllegalStateException("ModManager Not loaded");
        }
        if (!localModFiles.contains(from)) {
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
    }

    private Path backupMod(Path file) throws IOException {
        Path newPath = file.resolveSibling(
                StringUtils.addSuffix(
                        StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION),
                        OLD_EXTENSION
                )
        );
        if (Files.exists(file)) {
            Files.move(file, newPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return newPath;
    }

    private Path restoreMod(Path file) throws IOException {
        Path newPath = file.resolveSibling(
                StringUtils.removeSuffix(FileUtils.getName(file), OLD_EXTENSION)
        );
        if (Files.exists(file)) {
            Files.move(file, newPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return newPath;
    }

    public Path setOld(LocalModFile modFile, boolean old) throws IOException {
        Path newPath;
        if (old) {
            newPath = backupMod(modFile.getFile());
            localModFiles.remove(modFile);
        } else {
            newPath = restoreMod(modFile.getFile());
            localModFiles.add(modFile);
        }
        return newPath;
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

    public static String getModName(Path file) {
        return StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION, OLD_EXTENSION);
    }

    public boolean isOld(Path file) {
        return FileUtils.getName(file).endsWith(OLD_EXTENSION);
    }

    public boolean isDisabled(Path file) {
        return FileUtils.getName(file).endsWith(DISABLED_EXTENSION);
    }

    public static boolean isFileNameMod(Path file) {
        String name = getModName(file);
        return name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".litemod");
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

            if (Files.exists(fs.getPath("pack.mcmeta"))) {
                // resource pack, data pack
                return true;
            }

            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Check if "mods" directory has mod file named "fileName" no matter the mod is disabled or not
     *
     * @param fileName name of the file whose existence is being checked
     * @return true if the file exists
     */
    public boolean hasSimpleMod(String fileName) {
        return Files.exists(getModsDirectory().resolve(StringUtils.removeSuffix(fileName, DISABLED_EXTENSION)))
                || Files.exists(getModsDirectory().resolve(StringUtils.addSuffix(fileName, DISABLED_EXTENSION)));
    }

    public Path getSimpleModPath(String fileName) {
        return getModsDirectory().resolve(fileName);
    }

    public static final String DISABLED_EXTENSION = ".disabled";
    public static final String OLD_EXTENSION = ".old";
}
