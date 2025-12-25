package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/// Should implement `Comparable`
public sealed abstract class LocalAddonFile permits LocalModFile, ResourcePackFile {

    private final boolean keepOldFiles;

    protected LocalAddonFile(boolean keepOldFiles) {
        this.keepOldFiles = keepOldFiles;
    }

    public abstract Path getFile();

    /// Without extension
    public abstract String getFileName();

    public boolean isDisabled() {
        return FileUtils.getName(getFile()).endsWith(LocalFileManager.DISABLED_EXTENSION);
    }

    public abstract void markDisabled() throws IOException;

    public abstract void setOld(boolean old) throws IOException;

    public boolean keepOldFiles() {
        return keepOldFiles;
    }

    public abstract void delete() throws IOException;

    @Nullable
    public abstract ModUpdate checkUpdates(String gameVersion, RemoteModRepository repository) throws IOException;

    public record ModUpdate(LocalAddonFile localFile, RemoteMod.Version currentVersion,
                            List<RemoteMod.Version> candidates) {
    }

}
