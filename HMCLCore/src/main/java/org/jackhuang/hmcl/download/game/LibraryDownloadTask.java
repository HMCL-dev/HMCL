// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.download.DefaultCacheRepository;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.task.DownloadException;
import org.jackhuang.hmcl.task.FileDownloadTask.IntegrityCheck;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskSignificance;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Downloads a library or restores it from the cache, then publishes successful downloads to the cache.
public class LibraryDownloadTask extends Task<@Nullable Void> {
    protected final Path jar;
    protected final DefaultCacheRepository cacheRepository;
    protected final AbstractDependencyManager dependencyManager;
    protected final Library library;
    protected final String url;
    private final Library originalLibrary;

    public LibraryDownloadTask(AbstractDependencyManager dependencyManager, Path file, Library library) {
        this.dependencyManager = dependencyManager;
        this.originalLibrary = library;

        setSignificance(TaskSignificance.MODERATE);

        if (library.is("net.minecraftforge", "forge"))
            library = library.setClassifier("universal");

        this.library = library;
        this.cacheRepository = dependencyManager.getCacheRepository();

        url = library.getDownload().getUrl();
        jar = file;
    }

    /// Restores the library from the cache or downloads it and attempts to cache the verified file.
    ///
    /// @throws LibraryDownloadException if downloading the library fails
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        Optional<Path> libPath = cacheRepository.getLibrary(originalLibrary);
        if (libPath.isPresent()) {
            try {
                FileUtils.copyFile(libPath.get(), jar);
                return null;
            } catch (IOException e) {
                LOG.warning("Failed to copy file from cache", e);
                // We cannot copy cached file to current location
                // so we try to download a new one.
            }
        }

        DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
        DownloadCandidates urls = downloadProvider.getDownloadCandidates(url);
        var task = new FileDownloadTask(urls, jar,
                library.getDownload().getSha1() != null ? new IntegrityCheck("SHA-1", library.getDownload().getSha1()) : null);
        task.setCacheRepository(cacheRepository);
        task.setCaching(true);
        task.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);

        try {
            context.run(task);
        } catch (DownloadException e) {
            throw new LibraryDownloadException(library, e.getCause());
        } catch (CancellationException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new LibraryDownloadException(library, e);
        }

        try {
            cacheRepository.cacheLibrary(library, jar, false);
        } catch (IOException e) {
            LOG.warning("Failed to cache downloaded library " + library, e);
        }
        return null;
    }
}
