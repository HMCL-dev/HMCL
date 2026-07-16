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
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    public void delete() throws IOException {
        FileUtils.forceDeleteIfExists(getFile());
    }

    @Nullable
    public AddonUpdate checkUpdates(DownloadProvider downloadProvider, String gameVersion, RemoteAddon.Source source) throws IOException {
        return null;
    }

    public void onUpdated(String newFileNameWithExtension) {
    }

    public record AddonUpdate(
            LocalAddonFile localAddonFile,
            RemoteAddon.Version currentVersion,
            RemoteAddon.Version targetVersion,
            boolean useRemoteFileName
    ) {
    }

    public record Description(List<Part> parts) {
        public Description(String text) {
            this(new ArrayList<>());
            this.parts.add(new Part(text, "black"));
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            for (Part part : parts) {
                builder.append(part.text);
            }
            return builder.toString();
        }

        public String toStringSingleLine() {
            return toString().lines().map(String::trim).filter(StringUtils::isNotBlank).collect(Collectors.joining(" | "));
        }

        public record Part(String text, String color) {

            public Part {
                Objects.requireNonNull(text);
                Objects.requireNonNull(color);
            }

            public Part(String text) {
                this(text, "");
            }
        }
    }

}
