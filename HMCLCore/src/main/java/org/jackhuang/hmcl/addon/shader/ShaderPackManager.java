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
        ShaderPackFile shaderPackFile = ShaderPackFile.fromFile(file);
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
}
