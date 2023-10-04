package org.jackhuang.hmcl.download.neoforge;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.Task;

import java.util.Date;
import java.util.List;

public class NeoForgedRemoteVersion extends RemoteVersion {
    public NeoForgedRemoteVersion(String gameVersion, String selfVersion, Date releaseDate, List<String> urls) {
        super(LibraryAnalyzer.LibraryType.NEO_FORGED.getPatchId(), gameVersion, selfVersion, releaseDate, urls);
    }

    @Override
    public Task<Version> getInstallTask(DefaultDependencyManager dependencyManager, Version baseVersion) {
        throw new UnsupportedOperationException("Cannot install NeoForged automatically.");
    }
}
