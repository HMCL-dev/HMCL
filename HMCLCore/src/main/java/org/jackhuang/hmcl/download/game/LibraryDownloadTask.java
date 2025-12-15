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
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.task.DownloadException;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.FileDownloadTask.IntegrityCheck;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

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


        List<URI> uris = dependencyManager.getDownloadProvider().injectURLWithCandidates(url);
        task = new FileDownloadTask(uris, jar,
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

    public static boolean checksumValid(Path libPath, List<String> checksums) {
        try {
            if (checksums == null || checksums.isEmpty()) {
                return true;
            }
            byte[] fileData = Files.readAllBytes(libPath);
            boolean valid = checksums.contains(DigestUtils.digestToString("SHA-1", fileData));
            if (!valid && FileUtils.getName(libPath).endsWith(".jar")) {
                valid = validateJar(fileData, checksums);
            }
            return valid;
        } catch (IOException e) {
            LOG.warning("Failed to validate " + libPath, e);
        }
        return false;
    }

    private static boolean validateJar(byte[] data, List<String> checksums) throws IOException {
        HashMap<String, String> files = new HashMap<>();
        String[] hashes = null;
        JarInputStream jar = new JarInputStream(new ByteArrayInputStream(data));
        JarEntry entry = jar.getNextJarEntry();
        while (entry != null) {
            byte[] eData = jar.readAllBytes();
            if (entry.getName().equals("checksums.sha1")) {
                hashes = new String(eData, StandardCharsets.UTF_8).split("\n");
            }
            if (!entry.isDirectory()) {
                files.put(entry.getName(), DigestUtils.digestToString("SHA-1", eData));
            }
            entry = jar.getNextJarEntry();
        }
        jar.close();
        if (hashes != null) {
            boolean failed = !checksums.contains(files.get("checksums.sha1"));
            if (!failed) {
                for (String hash : hashes) {
                    if (!hash.trim().isEmpty() && hash.contains(" ")) {
                        String[] e = hash.split(" ");
                        String validChecksum = e[0];
                        String target = hash.substring(validChecksum.length() + 1);
                        String checksum = files.get(target);
                        if ((!files.containsKey(target)) || (checksum == null)) {
                            LOG.warning("    " + target + " : missing");
                            failed = true;
                            break;
                        } else if (!checksum.equals(validChecksum)) {
                            LOG.warning("    " + target + " : failed (" + checksum + ", " + validChecksum + ")");
                            failed = true;
                            break;
                        }
                    }
                }
            }
            return !failed;
        }
        return false;
    }
}
