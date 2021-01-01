/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;

/**
 *
 * @author huangyuhui
 */
public final class ModInfo implements Comparable<ModInfo> {

    private Path file;
    private final String name;
    private final String description;
    private final String authors;
    private final String version;
    private final String gameVersion;
    private final String url;
    private final String fileName;
    private final BooleanProperty activeProperty;

    public ModInfo(ModManager modManager, File file, String name, String description) {
        this(modManager, file, name, description, "", "", "", "");
    }

    public ModInfo(ModManager modManager, File file, String name, String description, String authors, String version, String gameVersion, String url) {
        this.file = file.toPath();
        this.name = name;
        this.description = description;
        this.authors = authors;
        this.version = version;
        this.gameVersion = gameVersion;
        this.url = url;

        activeProperty = new SimpleBooleanProperty(this, "active", !modManager.isDisabled(file)) {
            @Override
            protected void invalidated() {
                Path path = ModInfo.this.file.toAbsolutePath();

                try {
                    if (get())
                    	ModInfo.this.file = modManager.enableMod(path);
                    else
                    	ModInfo.this.file = modManager.disableMod(path);
                } catch (IOException e) {
                    Logging.LOG.log(Level.SEVERE, "Unable to invert state of mod file " + path, e);
                }
            }
        };

        fileName = StringUtils.substringBeforeLast(isActive() ? file.getName() : FileUtils.getNameWithoutExtension(file), '.');
    }

    public Path getFile() {
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

    public BooleanProperty activeProperty() {
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
}
