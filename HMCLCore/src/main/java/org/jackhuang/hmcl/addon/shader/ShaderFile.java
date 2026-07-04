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

import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public sealed abstract class ShaderFile extends LocalAddonFile permits ShaderZipFile, ShaderFolder {

    protected Path file;
    protected final String fileNameWithoutExtension;
    protected final ShaderLoaderType loaderType;
    protected final @Nullable ApertureData apertureData;

    protected ShaderFile(Path file, ShaderLoaderType loaderType, @Nullable ApertureData apertureData) {
        this.file = file;
        this.fileNameWithoutExtension = FileUtils.getNameWithoutExtension(file);
        this.loaderType = loaderType;
        this.apertureData = apertureData;
    }

    @Override
    public Path getFile() {
        return file;
    }

    @Override
    public String getFileName() {
        return fileNameWithoutExtension;
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

}
