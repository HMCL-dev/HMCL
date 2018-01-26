/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.mod;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.Validation;

import java.util.Collections;
import java.util.Map;

@Immutable
public final class ModpackConfiguration<T> implements Validation {

    private final T manifest;

    private final Map<String, FileInformation> overrides;

    public ModpackConfiguration() {
        this(null, Collections.emptyMap());
    }

    public ModpackConfiguration(T manifest, Map<String, FileInformation> overrides) {
        this.manifest = manifest;
        this.overrides = overrides;
    }

    public T getManifest() {
        return manifest;
    }

    public ModpackConfiguration<T> setManifest(T manifest) {
        return new ModpackConfiguration<>(manifest, overrides);
    }

    public ModpackConfiguration<T> setOverrides(Map<String, FileInformation> overrides) {
        return new ModpackConfiguration<>(manifest, overrides);
    }

    public Map<String, FileInformation> getOverrides() {
        return Collections.unmodifiableMap(overrides);
    }

    @Override
    public void validate() throws JsonParseException {
        if (manifest == null)
            throw new JsonParseException("MinecraftInstanceConfiguration missing `manifest`");
    }

    @Immutable
    public static class FileInformation implements Validation {
        private final String location; // relative
        private final String hash;
        private final String downloadURL;

        public FileInformation() {
            this(null, null);
        }

        public FileInformation(String location, String hash) {
            this(location, hash, null);
        }

        public FileInformation(String location, String hash, String downloadURL) {
            this.location = location;
            this.hash = hash;
            this.downloadURL = downloadURL;
        }

        public String getLocation() {
            return location;
        }

        public String getDownloadURL() {
            return downloadURL;
        }

        public String getHash() {
            return hash;
        }

        @Override
        public void validate() throws JsonParseException {
            if (location == null)
                throw new JsonParseException("FileInformation missing `location`.");
            if (hash == null)
                throw new JsonParseException("FileInformation missing file hash code.");
        }
    }
}
