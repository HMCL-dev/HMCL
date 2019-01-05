/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.Validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Immutable
public final class ModpackConfiguration<T> implements Validation {

    private final T manifest;
    private final String type;
    private final List<FileInformation> overrides;

    public ModpackConfiguration() {
        this(null, null, Collections.emptyList());
    }

    public ModpackConfiguration(T manifest, String type, List<FileInformation> overrides) {
        this.manifest = manifest;
        this.type = type;
        this.overrides = new ArrayList<>(overrides);
    }

    public T getManifest() {
        return manifest;
    }

    public String getType() {
        return type;
    }

    public ModpackConfiguration<T> setManifest(T manifest) {
        return new ModpackConfiguration<>(manifest, type, overrides);
    }

    public ModpackConfiguration<T> setOverrides(List<FileInformation> overrides) {
        return new ModpackConfiguration<>(manifest, type, overrides);
    }

    public List<FileInformation> getOverrides() {
        return Collections.unmodifiableList(overrides);
    }

    @Override
    public void validate() throws JsonParseException {
        if (manifest == null)
            throw new JsonParseException("MinecraftInstanceConfiguration missing `manifest`");
        if (type == null)
            throw new JsonParseException("MinecraftInstanceConfiguration missing `type`");
    }

    @Immutable
    public static class FileInformation implements Validation {
        private final String path; // relative
        private final String hash;
        private final String downloadURL;

        public FileInformation() {
            this(null, null);
        }

        public FileInformation(String path, String hash) {
            this(path, hash, null);
        }

        public FileInformation(String path, String hash, String downloadURL) {
            this.path = path;
            this.hash = hash;
            this.downloadURL = downloadURL;
        }

        /**
         * The relative path to Minecraft run directory
         * @return the relative path to Minecraft run directory.
         */
        public String getPath() {
            return path;
        }

        public String getDownloadURL() {
            return downloadURL;
        }

        public String getHash() {
            return hash;
        }

        @Override
        public void validate() throws JsonParseException {
            if (path == null)
                throw new JsonParseException("FileInformation missing `path`.");
            if (hash == null)
                throw new JsonParseException("FileInformation missing file hash code.");
        }
    }
}
