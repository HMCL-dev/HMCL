// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.pack.resourcepack;

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

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public sealed abstract class ResourcePackFile extends LocalAddonFile<ResourcePackFile> permits ResourcePackFolder, ResourcePackZipFile {

    static @Nullable ResourcePackFile fromFile(ResourcePackManager manager, Path path) {
        if (!isFileResourcePack(path)) return null;
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
    protected final PackMcMeta.PackInfo packInfo;

    protected final String fileName;
    protected final String fileNameWithExtension;

    private Compatibility compatibility = null;

    protected ResourcePackFile(ResourcePackManager manager, Path file, @Nullable PackMcMeta.PackInfo packInfo) {
        super();
        this.manager = manager;
        this.file = file;
        this.packInfo = packInfo;

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

    public @Nullable PackMcMeta.PackInfo getPackInfo() {
        return packInfo;
    }

    @Nullable
    public LocalAddonFile.Description getDescription() {
        if (getPackInfo() == null) return null;
        return getPackInfo().description();
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
