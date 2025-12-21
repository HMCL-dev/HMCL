package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public sealed interface ILocalFile permits LocalModFile, ResourcePackFile {

    Path getFile();

    /// Without extension
    String getFileName();

    default boolean isDisabled() {
        return FileUtils.getName(getFile()).endsWith(ModManager.DISABLED_EXTENSION);
    }

    void markDisabled() throws IOException;

    void setOld(boolean old) throws IOException;

    boolean keepOldFiles();

    void delete() throws IOException;

    @Nullable
    ModUpdate checkUpdates(String gameVersion, RemoteModRepository repository) throws IOException;

    record ModUpdate(ILocalFile localFile, RemoteMod.Version currentVersion,
                            List<RemoteMod.Version> candidates) {
    }

}
