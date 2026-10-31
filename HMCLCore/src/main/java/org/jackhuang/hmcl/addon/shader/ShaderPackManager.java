// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.shader;

import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ShaderPackManager extends LocalAddonManager<ShaderPackFile> {

    public ShaderPackManager(DefaultGameInstance instance) {
        super(instance);
    }

    private boolean loaded = false;

    @Override
    public Path getDirectory() {
        return instance.getShadersDirectory();
    }

    @Override
    public @Unmodifiable List<ShaderPackFile> getLocalFiles() throws IOException {
        lock.lock();
        try {
            if (!loaded)
                refresh();
            return super.getLocalFiles();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Comparator<ShaderPackFile> getComparator() {
        return ShaderPackFile::compareTo;
    }

    private void addShaderPackInfo(Path file) throws IOException {
        ShaderPackFile shaderPackFile = ShaderPackFile.fromFile(this, file);
        if (shaderPackFile != null) localFiles.add(shaderPackFile);
    }

    @Override
    public void refresh() throws IOException {
        lock.lock();
        try {
            localFiles.clear();

            if (Files.isDirectory(getDirectory())) {
                try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(getDirectory())) {
                    for (Path item : directoryStream) {
                        try {
                            addShaderPackInfo(item);
                        } catch (IOException e) {
                            LOG.warning("Failed to load resource pack " + item, e);
                        }
                    }
                }
            }

            loaded = true;
        } finally {
            lock.unlock();
        }
    }

    public void importShaderPack(Path file) throws IOException, IllegalArgumentException {
        if (ShaderPackFile.isFileShaderPack(file)) {
            Files.createDirectories(getDirectory());
            FileUtils.copyTo(file, getDirectory());

            loaded = false;
        } else {
            throw new IllegalArgumentException("File is not shader pack");
        }
    }

    public void removeShaderPacks(List<ShaderPackFile> files) throws IOException {
        for (var file : files) {
            file.delete();
        }

        loaded = false;
    }

    public void updateConfigFileName(ShaderPackFile pack, String newFileNameWithExt) {
        var configPath = pack.getFile().resolveSibling(pack.getFileNameWithExtension() + ".txt");
        if (Files.isRegularFile(configPath)) {
            var newConfigPath = pack.getFile().resolveSibling(newFileNameWithExt + ".txt");
            try {
                Files.move(configPath, newConfigPath);
            } catch (IOException e) {
                LOG.warning("Failed to rename shader config file " + configPath, e);
            }
        }
    }
}
