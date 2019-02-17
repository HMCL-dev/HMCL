/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.tukaani.xz.XZInputStream;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.DigestUtils.digest;
import static org.jackhuang.hmcl.util.Hex.encodeHex;

public class LibraryDownloadTask extends Task {
    private FileDownloadTask task;
    protected final File jar;
    protected final DefaultCacheRepository cacheRepository;
    private final File xzFile;
    protected final Library library;
    protected final String url;
    protected boolean xz;
    private final Library originalLibrary;
    private boolean cached = false;

    public LibraryDownloadTask(AbstractDependencyManager dependencyManager, File file, Library library) {
        this.originalLibrary = library;

        setSignificance(TaskSignificance.MODERATE);

        if (library.is("net.minecraftforge", "forge"))
            library = library.setClassifier("universal");

        this.library = library;
        this.cacheRepository = dependencyManager.getCacheRepository();

        url = dependencyManager.getDownloadProvider().injectURL(library.getDownload().getUrl());
        jar = file;

        xzFile = new File(file.getAbsoluteFile().getParentFile(), file.getName() + ".pack.xz");
    }

    @Override
    public Collection<? extends Task> getDependents() {
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
            Throwable t = task.getLastException();
            if (t instanceof DownloadException)
                throw new LibraryDownloadException(library, t.getCause());
            else
                throw new LibraryDownloadException(library, t);
        } else {
            if (xz) unpackLibrary(jar, Files.readAllBytes(xzFile.toPath()));
            if (!checksumValid(jar, library.getChecksums())) {
                jar.delete();
                throw new IOException("Checksum failed for " + library);
            }
        }
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        Optional<Path> libPath = cacheRepository.getLibrary(originalLibrary);
        if (libPath.isPresent()) {
            try {
                FileUtils.copyFile(libPath.get().toFile(), jar);
                cached = true;
                return;
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Failed to copy file from cache", e);
                // We cannot copy cached file to current location
                // so we try to download a new one.
            }
        }

        try {
            URL packXz = NetworkUtils.toURL(url + ".pack.xz");
            if (NetworkUtils.urlExists(packXz)) {
                task = new FileDownloadTask(packXz, xzFile, null);
                xz = true;
            } else {
                task = new FileDownloadTask(NetworkUtils.toURL(url),
                        jar,
                        library.getDownload().getSha1() != null ? new IntegrityCheck("SHA-1", library.getDownload().getSha1()) : null);
                xz = false;
            }
        } catch (IOException e) {
            throw new LibraryDownloadException(library, e);
        }
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        if (!cached)
            cacheRepository.cacheLibrary(library, jar.toPath(), xz);
    }

    public static boolean checksumValid(File libPath, List<String> checksums) {
        try {
            if (checksums == null || checksums.isEmpty()) {
                return true;
            }
            byte[] fileData = Files.readAllBytes(libPath.toPath());
            boolean valid = checksums.contains(encodeHex(digest("SHA-1", fileData)));
            if (!valid && libPath.getName().endsWith(".jar")) {
                valid = validateJar(fileData, checksums);
            }
            return valid;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean validateJar(byte[] data, List<String> checksums) throws IOException {
        HashMap<String, String> files = new HashMap<>();
        String[] hashes = null;
        JarInputStream jar = new JarInputStream(new ByteArrayInputStream(data));
        JarEntry entry = jar.getNextJarEntry();
        while (entry != null) {
            byte[] eData = IOUtils.readFullyWithoutClosing(jar);
            if (entry.getName().equals("checksums.sha1")) {
                hashes = new String(eData, Charset.forName("UTF-8")).split("\n");
            }
            if (!entry.isDirectory()) {
                files.put(entry.getName(), encodeHex(digest("SHA-1", eData)));
            }
            entry = jar.getNextJarEntry();
        }
        jar.close();
        if (hashes != null) {
            boolean failed = !checksums.contains(files.get("checksums.sha1"));
            if (!failed) {
                for (String hash : hashes) {
                    if ((!hash.trim().equals("")) && (hash.contains(" "))) {
                        String[] e = hash.split(" ");
                        String validChecksum = e[0];
                        String target = hash.substring(validChecksum.length() + 1);
                        String checksum = files.get(target);
                        if ((!files.containsKey(target)) || (checksum == null)) {
                            Logging.LOG.warning("    " + target + " : missing");
                            failed = true;
                            break;
                        } else if (!checksum.equals(validChecksum)) {
                            Logging.LOG.warning("    " + target + " : failed (" + checksum + ", " + validChecksum + ")");
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

    private static void unpackLibrary(File dest, byte[] src) throws IOException {
        if (dest.exists())
            if (!dest.delete())
                throw new IOException("Unable to delete file " + dest);

        byte[] decompressed = IOUtils.readFullyAsByteArray(new XZInputStream(new ByteArrayInputStream(src)));

        String end = new String(decompressed, decompressed.length - 4, 4);
        if (!end.equals("SIGN"))
            throw new IOException("Unpacking failed, signature missing " + end);

        int x = decompressed.length;
        int len = decompressed[(x - 8)] & 0xFF | (decompressed[(x - 7)] & 0xFF) << 8 | (decompressed[(x - 6)] & 0xFF) << 16 | (decompressed[(x - 5)] & 0xFF) << 24;

        File temp = FileUtils.createTempFile("minecraft", ".pack");

        byte[] checksums = Arrays.copyOfRange(decompressed, decompressed.length - len - 8, decompressed.length - 8);

        OutputStream out = new FileOutputStream(temp);
        out.write(decompressed, 0, decompressed.length - len - 8);
        out.close();

        try (FileOutputStream jarBytes = new FileOutputStream(dest); JarOutputStream jos = new JarOutputStream(jarBytes)) {
            Pack200.newUnpacker().unpack(temp, jos);

            JarEntry checksumsFile = new JarEntry("checksums.sha1");
            checksumsFile.setTime(0L);
            jos.putNextEntry(checksumsFile);
            jos.write(checksums);
            jos.closeEntry();
        }

        temp.delete();
    }
}
