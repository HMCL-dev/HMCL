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

    public static class Description {
        private final List<LocalAddonFile.Description.Part> parts;

        public Description(String text) {
            this.parts = new ArrayList<>();
            this.parts.add(new LocalAddonFile.Description.Part(text, "black"));
        }

        public Description(List<LocalAddonFile.Description.Part> parts) {
            this.parts = parts;
        }

        public List<LocalAddonFile.Description.Part> getParts() {
            return parts;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            for (LocalAddonFile.Description.Part part : parts) {
                builder.append(part.text);
            }
            return builder.toString();
        }

        public String toStringSingleLine() {
            return toString().lines().map(String::trim).filter(StringUtils::isNotBlank).collect(Collectors.joining(" | "));
        }

        public static class Part {
            private final String text;
            private final String color;

            public Part(String text) {
                this(text, "");
            }

            public Part(String text, String color) {
                this.text = Objects.requireNonNull(text);
                this.color = Objects.requireNonNull(color);
            }

            public String getText() {
                return text;
            }

            public String getColor() {
                return color;
            }
        }
    }

}
