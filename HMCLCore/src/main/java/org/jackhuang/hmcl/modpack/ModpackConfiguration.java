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
package org.jackhuang.hmcl.modpack;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Immutable
public record ModpackConfiguration<T>(T manifest, String type, String name, @Nullable String version,
                                      List<FileInformation> overrides) implements Validation {

    @SuppressWarnings("unchecked")
    public static <T> TypeToken<ModpackConfiguration<T>> typeOf(Class<T> clazz) {
        return (TypeToken<ModpackConfiguration<T>>) TypeToken.getParameterized(ModpackConfiguration.class, clazz);
    }

    public ModpackConfiguration {
        if (name == null) name = "";
        overrides = overrides == null ? List.of() : List.copyOf(overrides);
    }

    public ModpackConfiguration<T> withManifest(T manifest) {
        return new ModpackConfiguration<>(manifest, type, name, version, overrides);
    }

    public ModpackConfiguration<T> withOverrides(List<FileInformation> overrides) {
        return new ModpackConfiguration<>(manifest, type, name, version, overrides);
    }

    public ModpackConfiguration<T> withVersion(String version) {
        return new ModpackConfiguration<>(manifest, type, name, version, overrides);
    }

    @Override
    public void validate() throws JsonParseException {
        if (manifest == null)
            throw new JsonParseException("MinecraftInstanceConfiguration missing `manifest`");
        if (type == null)
            throw new JsonParseException("MinecraftInstanceConfiguration missing `type`");
    }

    /**
     * @param path the relative path to Minecraft run directory.
     */
    @Immutable
    public record FileInformation(String path, String hash, @Nullable String downloadURL) implements Validation {

        public FileInformation(String path, String hash) {
            this(path, hash, null);
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
