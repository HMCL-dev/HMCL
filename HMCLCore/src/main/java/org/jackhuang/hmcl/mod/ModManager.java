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

import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Collection;
import java.util.TreeSet;

public final class ModManager {
    private final GameRepository repository;
    private final String id;
    private final TreeSet<LocalMod> localMods = new TreeSet<>();

    private boolean loaded = false;

    public ModManager(GameRepository repository, String id) {
        this.repository = repository;
        this.id = id;
    }

    public GameRepository getRepository() {
        return repository;
    }

    public String getVersion() {
        return id;
    }

    private Path getModsDirectory() {
        return repository.getRunDirectory(id).toPath().resolve("mods");
    }

    private void addModInfo(File file) {
        try {
            localMods.add(getModInfo(file));
        } catch (IllegalArgumentException ignore) {
        }
    }

    public static LocalMod getModInfo(File modFile) {
        File file = isDisabled(modFile) ? new File(modFile.getAbsoluteFile().getParentFile(), FileUtils.getNameWithoutExtension(modFile)) : modFile;
        String description, extension = FileUtils.getExtension(file);
        switch (extension) {
            case "zip":
            case "jar":
                try {
                    return ForgeOldModMetadata.fromFile(modFile);
                } catch (Exception ignore) {
                }

                try {
                    return ForgeNewModMetadata.fromFile(modFile);
                } catch (Exception ignore) {
                }

                try {
                    return FabricModMetadata.fromFile(modFile);
                } catch (Exception ignore) {
                }

                try {
                    return PackMcMeta.fromFile(modFile);
                } catch (Exception ignore) {
                }

                description = "";
                break;
            case "litemod":
                try {
                    return LiteModMetadata.fromFile(modFile);
                } catch (Exception ignore) {
                    description = "LiteLoader Mod";
                }
                break;
            default:
                throw new IllegalArgumentException("File " + modFile + " is not a mod file.");
        }
        return new LocalMod(modFile, ModLoaderType.UNKNOWN, null, FileUtils.getNameWithoutExtension(modFile), new LocalMod.Description(description));
    }

    public void refreshMods() throws IOException {
        localMods.clear();
        if (Files.isDirectory(getModsDirectory())) {
            try (DirectoryStream<Path> modsDirectoryStream = Files.newDirectoryStream(getModsDirectory())) {
                for (Path subitem : modsDirectoryStream) {
                    if (Files.isDirectory(subitem) && VersionNumber.isIntVersionNumber(FileUtils.getName(subitem))) {
                        // If the folder name is game version, forge will search mod in this subdirectory
                        try (DirectoryStream<Path> subitemDirectoryStream = Files.newDirectoryStream(subitem)) {
                            for (Path subsubitem : subitemDirectoryStream) {
                                addModInfo(subsubitem.toFile());
                            }
                        }
                    } else {
                        addModInfo(subitem.toFile());
                    }
                }
            }
        }
        loaded = true;
    }

    public Collection<LocalMod> getMods() throws IOException {
        if (!loaded)
            refreshMods();
        return localMods;
    }

    public void addMod(File file) throws IOException {
        if (!isFileNameMod(file))
            throw new IllegalArgumentException("File " + file + " is not a valid mod file.");

        if (!loaded)
            refreshMods();

        File modsDirectory = new File(repository.getRunDirectory(id), "mods");
        if (!FileUtils.makeDirectory(modsDirectory))
            throw new IOException("Cannot make directory " + modsDirectory);

        File newFile = new File(modsDirectory, file.getName());
        FileUtils.copyFile(file, newFile);

        addModInfo(newFile);
    }

    public void removeMods(LocalMod... localMods) throws IOException {
        for (LocalMod localMod : localMods) {
            Files.deleteIfExists(localMod.getFile());
        }
    }

    public static Path disableMod(Path file) throws IOException {
        Path disabled = file.getParent().resolve(StringUtils.addSuffix(FileUtils.getName(file), DISABLED_EXTENSION));
        if (Files.exists(file))
            Files.move(file, disabled, StandardCopyOption.REPLACE_EXISTING);
        return disabled;
    }

    public static Path enableMod(Path file) throws IOException {
        Path enabled = file.getParent().resolve(StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION));
        if (Files.exists(file))
            Files.move(file, enabled, StandardCopyOption.REPLACE_EXISTING);
        return enabled;
    }

    public static boolean isDisabled(File file) {
        return file.getPath().endsWith(DISABLED_EXTENSION);
    }

    public static boolean isFileNameMod(File file) {
        String name = file.getName();
        if (isDisabled(file))
            name = FileUtils.getNameWithoutExtension(file);
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

    public static String getMcmodUrl(String mcmodId) {
        return String.format("https://www.mcmod.cn/class/%s.html", mcmodId);
    }

    public static String getMcbbsUrl(String mcbbsId) {
        return String.format("https://www.mcbbs.net/thread-%s-1-1.html", mcbbsId);
    }

    public static final String DISABLED_EXTENSION = ".disabled";
}
