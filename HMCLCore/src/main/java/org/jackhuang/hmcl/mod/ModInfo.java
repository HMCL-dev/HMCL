/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.ImmediateBooleanProperty;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.File;
import java.util.Objects;

/**
 *
 * @author huangyuhui
 */
public final class ModInfo implements Comparable<ModInfo> {

    private File file;
    private final String name;
    private final String description;
    private final String authors;
    private final String version;
    private final String gameVersion;
    private final String url;
    private final String fileName;
    private final ImmediateBooleanProperty activeProperty;

    public ModInfo(File file, String name) {
        this(file, name, "");
    }

    public ModInfo(File file, String name, String description) {
        this(file, name, description, "unknown", "unknown", "unknown", "");
    }

    public ModInfo(File file, String name, String description, String authors, String version, String gameVersion, String url) {
        this.file = file;
        this.name = name;
        this.description = description;
        this.authors = authors;
        this.version = version;
        this.gameVersion = gameVersion;
        this.url = url;

        activeProperty = new ImmediateBooleanProperty(this, "active", !DISABLED_EXTENSION.equals(FileUtils.getExtension(file))) {
            @Override
            protected void invalidated() {
                File f = ModInfo.this.file.getAbsoluteFile(), newF;
                if (DISABLED_EXTENSION.equals(FileUtils.getExtension(f)))
                    newF = new File(f.getParentFile(), FileUtils.getNameWithoutExtension(f));
                else
                    newF = new File(f.getParentFile(), f.getName() + ".disabled");

                if (f.renameTo(newF))
                    ModInfo.this.file = newF;
                else
                    Logging.LOG.severe("Unable to rename file " + f + " to " + newF);
            }
        };

        fileName = StringUtils.substringBeforeLast(isActive() ? file.getName() : FileUtils.getNameWithoutExtension(file), '.');
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthors() {
        return authors;
    }

    public String getVersion() {
        return version;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public String getUrl() {
        return url;
    }

    public ImmediateBooleanProperty activeProperty() {
        return activeProperty;
    }

    public boolean isActive() {
        return activeProperty.get();
    }

    public void setActive(boolean active) {
        activeProperty.set(active);
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public int compareTo(ModInfo o) {
        return getFileName().compareTo(o.getFileName());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ModInfo && Objects.equals(getFileName(), ((ModInfo) obj).getFileName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFileName());
    }

    public static boolean isFileMod(File file) {
        String name = file.getName();
        if (isDisabled(file))
            name = FileUtils.getNameWithoutExtension(file);
        return name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".litemod");
    }

    public static ModInfo fromFile(File modFile) {
        File file = isDisabled(modFile) ? new File(modFile.getAbsoluteFile().getParentFile(), FileUtils.getNameWithoutExtension(modFile)) : modFile;
        String description, extension = FileUtils.getExtension(file);
        switch (extension) {
            case "zip":
            case "jar":
                try {
                    return ForgeModMetadata.fromFile(modFile);
                } catch (Exception ignore) {
                    description = "May be Forge mod";
                }
                break;
            case "litemod":
                try {
                    return LiteModMetadata.fromFile(modFile);
                } catch (Exception ignore) {
                    description = "May be LiteLoader mod";
                }
                break;
            default:
                throw new IllegalArgumentException("File " + modFile + " is not a mod file.");
        }
        return new ModInfo(modFile, FileUtils.getNameWithoutExtension(modFile), description);
    }

    public static boolean isDisabled(File file) {
        return DISABLED_EXTENSION.equals(FileUtils.getExtension(file));
    }

    public static final String DISABLED_EXTENSION = "disabled";
}
