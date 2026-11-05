// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.datapack;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.meta.PackMcMeta;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.addon.LocalAddonManager.DISABLED_EXTENSION;

@NotNullByDefault
public class DataPackFile extends LocalAddonFile<DataPackFile> {
    final DataPackManager manager;
    private Path path;
    private final boolean isDirectory;
    private Path statusFile;
    private final ReadOnlyBooleanWrapper activeProperty;
    private final String id;
    private final PackMcMeta.PackInfo info;

    public DataPackFile(DataPackManager manager, Path path) throws IOException {
        this.manager = manager;
        this.path = path;
        isDirectory = Files.isDirectory(path);

        String name = FileUtils.getName(path);
        id = isDirectory ? name : FileUtils.getNameWithoutExtension(StringUtils.removeSuffix(name, DISABLED_EXTENSION));

        if (isDirectory) {
            Path mcmeta1 = path.resolve("pack.mcmeta");
            Path mcmetaPath = Files.exists(mcmeta1) ? mcmeta1 : path.resolve("pack.mcmeta.disabled");
            try {
                info = JsonUtils.fromNonNullJson(Files.readString(mcmetaPath), PackMcMeta.class).pack();
            } catch (Exception e) {
                throw new IOException(e);
            }
            statusFile = mcmetaPath;
        } else {
            try (var tree = CompressingUtils.openZipTree(path)) {
                info = JsonUtils.fromNonNullJson(tree.readTextEntry("/pack.mcmeta"), PackMcMeta.class).pack();
            } catch (Exception e) {
                throw new IOException(e);
            }
            statusFile = path;
        }

        this.activeProperty = new ReadOnlyBooleanWrapper(this, "active", !FileUtils.getName(statusFile).endsWith(DISABLED_EXTENSION));
    }

    public PackMcMeta.PackInfo getInfo() {
        return info;
    }

    public ReadOnlyBooleanProperty activeProperty() {
        return activeProperty.getReadOnlyProperty();
    }

    public boolean isActive() {
        return activeProperty.get();
    }

    public void setActive(boolean active) throws IOException {
        if (active == isActive())
            return;

        Path newStatusFile = active
                ? statusFile.resolveSibling(FileUtils.getNameWithoutExtension(statusFile))
                : statusFile.resolveSibling(FileUtils.getName(statusFile) + DISABLED_EXTENSION);

        Files.move(statusFile, newStatusFile);
        statusFile = newStatusFile;
        if (!isDirectory)
            path = newStatusFile;

        activeProperty.set(active);
    }

    @Override
    public Path getFile() {
        return path;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    @Override
    public String getFileName() {
        return id;
    }

    @Override
    public void markDisabled() {
    }

    @Override
    public void setOld(boolean old) {
    }

    @Override
    public boolean keepOldFiles() {
        return false;
    }

    @Override
    public void delete() throws IOException {
        if (isDirectory)
            FileUtils.deleteDirectory(path);
        else
            Files.delete(path);
    }

    @Override
    public int compareTo(DataPackFile o) {
        return String.CASE_INSENSITIVE_ORDER.compare(id, o.id);
    }
}
