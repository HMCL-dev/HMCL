/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod.modinfo;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModManager;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public interface IModMetadataReader {
    LocalModFile fromFile(ModManager modManager, Path modFile, FileSystem fs) throws IOException, JsonParseException;

    static Map<String, MetadataReaderStorage> ofStorages(MetadataReaderStorage... storages) {
        Map<String, MetadataReaderStorage> storageMap = new HashMap<>();
        for (MetadataReaderStorage storage : storages) {
            if (storage.extensions == null || storage.defaultDesc == null) {
                throw new IllegalArgumentException();
            }
            for (String extension : storage.extensions) {
                storageMap.put(extension, storage);
            }
            storage.extensions = null;
        }
        return storageMap;
    }

    static MetadataReaderStorage ofExtensions(String... extensions) {
        return new MetadataReaderStorage(extensions);
    }

    final class MetadataReaderStorage {
        private String[] extensions;

        private IModMetadataReader[] readers = new IModMetadataReader[0];

        private String defaultDesc = null;

        private MetadataReaderStorage(String... extensions) {
            this.extensions = extensions;
        }

        public MetadataReaderStorage ofReaders(IModMetadataReader... readers) {
            this.readers = readers;
            return this;
        }

        public MetadataReaderStorage ofDefaultDesc(String defaultDesc) {
            this.defaultDesc = defaultDesc;
            return this;
        }

        public IModMetadataReader[] getReaders() {
            return this.readers;
        }

        public String getDefaultDesc() {
            return this.defaultDesc;
        }
    }
}
