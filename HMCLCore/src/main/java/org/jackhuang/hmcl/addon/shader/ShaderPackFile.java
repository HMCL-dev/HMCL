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

import javafx.scene.image.Image;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public sealed abstract class ShaderPackFile extends LocalAddonFile implements Comparable<ShaderPackFile> permits ShaderPackZipFile, ShaderPackFolder {
    public static @Nullable ShaderPackFile fromFile(ShaderPackManager manager, Path file) throws IOException {
        return Files.isRegularFile(file) ? ShaderPackZipFile.load(manager, file) : ShaderPackFolder.load(manager, file);
    }

    public static boolean isFileShaderPack(Path file) {
        if (Files.isDirectory(file)) return Files.isDirectory(file.resolve("shaders"));
        if (Files.isRegularFile(file)) {
            try (var zipSystem = CompressingUtils.createReadOnlyZipFileSystem(file)) {
                Path root = zipSystem.getRootDirectories().iterator().next();
                try (Stream<Path> stream = Files.walk(root)) {
                    return stream.filter(path -> path.endsWith("shaders")).anyMatch(Files::isDirectory);
                }
            } catch (IOException e) {
                LOG.warning("Failed to check if file is shader pack", e);
            }
        }
        return false;
    }

    protected final ShaderPackManager manager;
    protected Path file;
    protected final ShaderLoaderType loaderType;
    protected final ShaderPackMeta meta;

    protected final String fileName;
    protected final String fileNameWithExtension;

    protected ShaderPackFile(ShaderPackManager manager, Path file, ShaderLoaderType loaderType, @Nullable ShaderPackMeta meta) {
        this.manager = manager;
        this.file = file;
        this.loaderType = loaderType;
        this.meta = Objects.requireNonNullElse(meta, ShaderPackMeta.EMPTY);

        this.fileName = FileUtils.getNameWithoutExtension(file);
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

    public ShaderLoaderType getLoaderType() {
        return loaderType;
    }

    public ShaderPackMeta getMeta() {
        return meta;
    }

    public abstract @Nullable Image loadIcon();

    public String getName() {
        return StringUtils.isBlank(getMeta().name()) ? getFile().getFileName().toString() : getMeta().name();
    }

    @Override
    public boolean isDisabled() {
        return false;
    }

    @Override
    public void markDisabled() {
        // NO-OP
    }

    @Override
    public boolean keepOldFiles() {
        return false;
    }

    @Override
    public void setOld(boolean old) throws IOException {
        if (old) file = LocalAddonManager.backupFile(file);
        else file = LocalAddonManager.restoreFile(file);
    }

    @Override
    public int compareTo(ShaderPackFile other) {
        return fileName.compareTo(other.fileName);
    }

    @Override
    public void onUpdated(String newFileNameWithExt) {
        super.onUpdated(newFileNameWithExt);
        manager.updateConfigFileName(this, newFileNameWithExt);
    }
}
