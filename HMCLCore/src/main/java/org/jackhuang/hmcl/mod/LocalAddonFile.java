package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/// Sub-classes should implement `Comparable`
public abstract class LocalAddonFile {

    private final boolean keepOldFiles;

    protected LocalAddonFile(boolean keepOldFiles) {
        this.keepOldFiles = keepOldFiles;
    }

    public abstract Path getFile();

    /// Without extension
    public abstract String getFileName();

    public boolean isDisabled() {
        return FileUtils.getName(getFile()).endsWith(LocalAddonManager.DISABLED_EXTENSION);
    }

    public abstract void markDisabled() throws IOException;

    public abstract void setOld(boolean old) throws IOException;

    public boolean keepOldFiles() {
        return keepOldFiles;
    }

    public abstract void delete() throws IOException;

    @Nullable
    public abstract AddonUpdate checkUpdates(String gameVersion, RemoteMod.Type type) throws IOException;

    public record AddonUpdate(LocalAddonFile localAddonFile, RemoteMod.Version currentVersion,
                              RemoteMod.Version candidate) {
    }

}
