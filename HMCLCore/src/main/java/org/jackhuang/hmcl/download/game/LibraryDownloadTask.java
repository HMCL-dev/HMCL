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
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Downloads a library or restores it from the cache, then publishes successful downloads to the cache.
public class LibraryDownloadTask extends Task<Void> {
    private FileDownloadTask task;
    protected final Path jar;
    protected final DefaultCacheRepository cacheRepository;
    protected final AbstractDependencyManager dependencyManager;
    protected final Library library;
    protected final String url;
    private final Library originalLibrary;
    private boolean cached = false;

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

    @Override
    public Collection<Task<?>> getDependents() {
        if (cached) return Collections.emptyList();
        else return Collections.singleton(task);
    }

    @Override
    public boolean isRelyingOnDependents() {
        return false;
    }

    @Override
    public void execute() throws Exception {
        if (cached) return;

        if (!isDependentsSucceeded()) {
            // Since FileDownloadTask wraps the actual exception with DownloadException.
            // We should extract it letting the error message clearer.
            Exception t = task.getException();
            if (t instanceof DownloadException)
                throw new LibraryDownloadException(library, t.getCause());
            else if (t instanceof CancellationException)
                throw new CancellationException();
            else
                throw new LibraryDownloadException(library, t);
        }
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() {
        Optional<Path> libPath = cacheRepository.getLibrary(originalLibrary);
        if (libPath.isPresent()) {
            try {
                FileUtils.copyFile(libPath.get(), jar);
                cached = true;
                return;
            } catch (IOException e) {
                LOG.warning("Failed to copy file from cache", e);
                // We cannot copy cached file to current location
                // so we try to download a new one.
            }
        }


        DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
        DownloadCandidates urls = downloadProvider.getDownloadCandidates(url);
        task = new FileDownloadTask(urls, jar,
                library.getDownload().getSha1() != null ? new IntegrityCheck("SHA-1", library.getDownload().getSha1()) : null);
        task.setCacheRepository(cacheRepository);
        task.setCaching(true);
        task.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        if (!cached) {
            try {
                cacheRepository.cacheLibrary(library, jar, false);
            } catch (IOException e) {
                LOG.warning("Failed to cache downloaded library " + library, e);
            }
        }
    }
}
