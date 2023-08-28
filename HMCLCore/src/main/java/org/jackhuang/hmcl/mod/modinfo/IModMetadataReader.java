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
