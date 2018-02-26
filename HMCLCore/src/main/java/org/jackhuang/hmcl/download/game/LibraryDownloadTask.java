package org.jackhuang.hmcl.download.game;

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Scheduler;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.*;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;

public final class LibraryDownloadTask extends Task {
    private final FileDownloadTask xzTask;
    private final FileDownloadTask task;
    private final File jar;
    private final File xzFile;
    private final Library library;

    public LibraryDownloadTask(AbstractDependencyManager dependencyManager, File file, Library library) {
        String url = dependencyManager.getDownloadProvider().injectURL(library.getDownload().getUrl());
        jar = file;
        this.library = library;
        xzFile = new File(file.getAbsoluteFile().getParentFile(), file.getName() + ".pack.xz");

        xzTask = new FileDownloadTask(NetworkUtils.toURL(url + ".pack.xz"),
                xzFile, dependencyManager.getProxy(), null, 1);

        task = new FileDownloadTask(NetworkUtils.toURL(url + ".pack.xz"),
                file, dependencyManager.getProxy(), library.getDownload().getSha1());
    }

    @Override
    public Collection<? extends Task> getDependents() {
        return Collections.singleton(xzTask);
    }

    @Override
    public boolean isRelyingOnDependents() {
        return false;
    }

    @Override
    public Scheduler getScheduler() {
        return Schedulers.io();
    }

    @Override
    public void execute() throws Exception {
        if (isDependentsSucceeded()) {
            unpackLibrary(jar, FileUtils.readBytes(xzFile));
            if (!checksumValid(jar, library.getChecksums()))
                throw new IOException("Checksum failed for " + library);
        }
    }

    @Override
    public Collection<? extends Task> getDependencies() {
        return isDependentsSucceeded() ? Collections.emptySet() : Collections.singleton(task);
    }

    private static boolean checksumValid(File libPath, List<String> checksums) {
        try {
            if ((checksums == null) || (checksums.isEmpty())) {
                return true;
            }
            byte[] fileData = FileUtils.readBytes(libPath);
            boolean valid = checksums.contains(DigestUtils.sha1Hex(fileData));
            if ((!valid) && (libPath.getName().endsWith(".jar"))) {
            }
            return validateJar(fileData, checksums);
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
            byte[] eData = IOUtils.readFullyAsByteArray(jar);
            if (entry.getName().equals("checksums.sha1")) {
                hashes = new String(eData, Charset.forName("UTF-8")).split("\n");
            }
            if (!entry.isDirectory()) {
                files.put(entry.getName(), DigestUtils.sha1Hex(eData));
            }
            entry = jar.getNextJarEntry();
        }
        jar.close();
        if (hashes != null) {
            boolean passed = !checksums.contains(files.get("checksums.sha1"));
            if (passed) {
                for (String hash : hashes) {
                    if ((!hash.trim().equals("")) && (hash.contains(" "))) {
                        String[] e = hash.split(" ");
                        String validChecksum = e[0];
                        String target = hash.substring(validChecksum.length() + 1);
                        String checksum = files.get(target);
                        if ((!files.containsKey(target)) || (checksum == null)) {
                            Logging.LOG.warning("    " + target + " : missing");
                            passed = false;
                            break;
                        } else if (!checksum.equals(validChecksum)) {
                            Logging.LOG.warning("    " + target + " : failed (" + checksum + ", " + validChecksum + ")");
                            passed = false;
                            break;
                        }
                    }
                }
            }
            return passed;
        }
        return false;
    }

    private static void unpackLibrary(File dest, byte[] src) throws IOException {
        if (dest.exists())
            if (!dest.delete())
                throw new IOException("Unable to delete file " + dest);

        byte[] decompressed = IOUtils.readFullyAsByteArray(new XZCompressorInputStream(new ByteArrayInputStream(src)));

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
