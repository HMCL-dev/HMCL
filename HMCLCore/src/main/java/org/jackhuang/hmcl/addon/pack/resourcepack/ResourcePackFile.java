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
package org.jackhuang.hmcl.addon.pack.resourcepack;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.pack.PackMcMeta;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public sealed abstract class ResourcePackFile extends LocalAddonFile implements Comparable<ResourcePackFile> permits ResourcePackFolder, ResourcePackZipFile {
    static ResourcePackFile fromFile(ResourcePackManager manager, Path path) throws IOException {
        return Files.isRegularFile(path) ? ResourcePackZipFile.load(manager, path) : ResourcePackFolder.load(manager, path);
    }

    public static boolean isFileResourcePack(Path file) {
        if (Files.isDirectory(file)) return Files.isRegularFile(file.resolve("pack.mcmeta"));
        if (Files.isRegularFile(file) && file.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (var zipFileTree = CompressingUtils.openZipFile(file)) {
                return zipFileTree.getEntry("pack.mcmeta") != null;
            } catch (IOException e) {
                LOG.warning("Failed to check if file is resource pack", e);
            }
        }
        return false;
    }

    protected final ResourcePackManager manager;
    protected Path file;
    protected final PackMcMeta meta;
    protected final Image icon;

    protected final String fileName;
    protected final String fileNameWithExtension;

    private Compatibility compatibility = null;

    protected ResourcePackFile(ResourcePackManager manager, Path file, @NotNull PackMcMeta meta, @Nullable Image icon) {
        super();
        this.manager = manager;
        this.file = file;
        this.meta = Objects.requireNonNull(meta);
        this.icon = icon;

        this.fileName = StringUtils.parseColorEscapes(FileUtils.getNameWithoutExtension(file));
        this.fileNameWithExtension = file.getFileName().toString();
    }

    @Override
    public Path getFile() {
        return file;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    public String getFileNameWithExtension() {
        return fileNameWithExtension;
    }

    public Compatibility getCompatibility() {
        if (compatibility == null) {
            compatibility = manager.getCompatibility(this);
        }
        return compatibility;
    }

    public boolean isCompatible() {
        return getCompatibility() == Compatibility.COMPATIBLE;
    }

    public boolean isEnabled() {
        return manager.isEnabled(this);
    }

    public void setEnabled(boolean enabled) {
        if (enabled) {
            manager.enableResourcePacks(List.of(this));
        } else {
            manager.disableResourcePacks(List.of(this));
        }
    }

    @Override
    public void setOld(boolean old) throws IOException {
        this.file = manager.setOld(this, old);
    }

    @Override
    public final boolean keepOldFiles() {
        return false;
    }

    @Override
    public void markDisabled() {
    }

    public @NotNull PackMcMeta getMeta() {
        return meta;
    }

    // 64 * 64
    public @Nullable Image getIcon() {
        return icon;
    }

    @Nullable
    public LocalAddonFile.Description getDescription() {
        if (getMeta().pack() == null) return null;
        return getMeta().pack().description();
    }

    @Override
    public int compareTo(@NotNull ResourcePackFile other) {
        return this.fileNameWithExtension.compareTo(other.fileNameWithExtension);
    }

    public enum Compatibility {
        COMPATIBLE,
        TOO_NEW,
        TOO_OLD,
        INVALID,
        MISSING_PACK_META,
        MISSING_GAME_META
    }
}
