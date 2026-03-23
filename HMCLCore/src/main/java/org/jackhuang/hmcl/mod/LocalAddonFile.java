/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/// Sub-classes should implement `Comparable`
public abstract class LocalAddonFile {

    protected LocalAddonFile() {
    }

    public abstract Path getFile();

    /// Without extension
    public abstract String getFileName();

    public boolean isDisabled() {
        return FileUtils.getName(getFile()).endsWith(LocalAddonManager.DISABLED_EXTENSION);
    }

    public abstract void markDisabled() throws IOException;

    public abstract void setOld(boolean old) throws IOException;

    public abstract boolean keepOldFiles();

    public abstract void delete() throws IOException;

    @Nullable
    public abstract AddonUpdate checkUpdates(DownloadProvider downloadProvider, String gameVersion, RemoteMod.Type type) throws IOException;

    public record AddonUpdate(
            LocalAddonFile localAddonFile,
            RemoteMod.Version currentVersion,
            RemoteMod.Version targetVersion,
            boolean useRemoteFileName
    ) {
    }

}
