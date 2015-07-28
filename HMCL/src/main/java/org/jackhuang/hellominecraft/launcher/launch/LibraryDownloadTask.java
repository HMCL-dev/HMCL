/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.launch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.tasks.download.NetException;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.tukaani.xz.XZInputStream;

/**
 *
 * @author huangyuhui
 */
public class LibraryDownloadTask extends Task {

    private static final int MAX_BUFFER_SIZE = 2048;

    GameLauncher.DownloadLibraryJob job;

    public LibraryDownloadTask(GameLauncher.DownloadLibraryJob job) {
        this.job = job;
    }
    
    @Override
    public boolean executeTask() {
        try {
            File packFile = new File(job.path.getParentFile(), job.path.getName() + ".pack.xz");
            if (job.url.contains("typesafe") && download(new URL(job.url + ".pack.xz"), packFile)) {
                unpackLibrary(job.path, packFile);
                packFile.delete();
                return true;
            } else {
                return download(new URL(job.url), job.path);
            }
        } catch (Exception ex) {
            setFailReason(ex);
            return false;
        }
    }

    InputStream stream;
    RandomAccessFile file;
    boolean shouldContinue = true, aborted = false;
    int size = -1;

    boolean download(URL url, File filePath) {
        HMCLog.log("Downloading: " + url + " to " + filePath);
        size = -1;
        int downloaded = 0;
        for (int repeat = 0; repeat < 6; repeat++) {
            if (repeat > 0)
                HMCLog.warn("Failed to download, repeat: " + repeat);
            try {

                // Open connection to URL.
                HttpURLConnection connection
                        = (HttpURLConnection) url.openConnection();

                connection.setConnectTimeout(5000);
                connection.setRequestProperty("User-Agent", "Hello Minecraft! Launcher");

                // Connect to server.
                connection.connect();

                // Make sure response code is in the 200 range.
                if (connection.getResponseCode() / 100 != 2) {
                    setFailReason(new NetException(C.i18n("download.not_200") + " " + connection.getResponseCode()));
                    return false;
                }

                // Check for valid content length.
                int contentLength = connection.getContentLength();
                if (contentLength < 1) {
                    setFailReason(new NetException("The content length is invalid."));
                    return false;
                }

                // Set the size for this download if it hasn't been already set.
                if (size == -1)
                    size = contentLength;

                filePath.getParentFile().mkdirs();

                File tempFile = new File(filePath.getAbsolutePath() + ".hmd");
                if (!tempFile.exists())
                    tempFile.createNewFile();

                // Open file and seek to the end of it.
                file = new RandomAccessFile(tempFile, "rw");
                file.seek(downloaded);
                stream = connection.getInputStream();
                while (true) {
                    // Size buffer according to how much of the file is left to download.
                    if (!shouldContinue) {
                        closeFiles();
                        filePath.delete();
                        break;
                    }

                    byte buffer[] = new byte[MAX_BUFFER_SIZE];

                    // Read from server into buffer.
                    int read = stream.read(buffer);
                    if (read == -1)
                        break;

                    // Write buffer to file.
                    file.write(buffer, 0, read);
                    downloaded += read;

                    if (ppl != null)
                        ppl.setProgress(this, downloaded, size);
                }
                closeFiles();
                tempFile.renameTo(filePath);
                if (ppl != null)
                    ppl.onProgressProviderDone(this);
                return true;
            } catch (Exception e) {
                setFailReason(new NetException(C.i18n("download.failed") + " " + url, e));
            } finally {
                closeFiles();
            }
        }
        return false;
    }

    public static void unpackLibrary(File output, File input)
            throws IOException {
        HMCLog.log("Unpacking " + output);
        if (output.exists())
            output.delete();

        byte[] decompressed = IOUtils.readFully(new XZInputStream(new FileInputStream(input)));

        String end = new String(decompressed, decompressed.length - 4, 4);
        if (!end.equals("SIGN")) {
            HMCLog.log("Unpacking failed, signature missing " + end);
            return;
        }

        int x = decompressed.length;
        int len = decompressed[(x - 8)] & 0xFF | (decompressed[(x - 7)] & 0xFF) << 8 | (decompressed[(x - 6)] & 0xFF) << 16 | (decompressed[(x - 5)] & 0xFF) << 24;

        File temp = File.createTempFile("art", ".pack");
        HMCLog.log("  Signed");
        HMCLog.log("  Checksum Length: " + len);
        HMCLog.log("  Total Length:    " + (decompressed.length - len - 8));
        HMCLog.log("  Temp File:       " + temp.getAbsolutePath());

        byte[] checksums = Arrays.copyOfRange(decompressed, decompressed.length - len - 8, decompressed.length - 8);

        try (OutputStream out = new FileOutputStream(temp)) {
            out.write(decompressed, 0, decompressed.length - len - 8);
        }
        decompressed = null;
        System.gc();

        try (FileOutputStream jarBytes = new FileOutputStream(output); JarOutputStream jos = new JarOutputStream(jarBytes)) {
            
            Pack200.newUnpacker().unpack(temp, jos);
            
            JarEntry checksumsFile = new JarEntry("checksums.sha1");
            checksumsFile.setTime(0L);
            jos.putNextEntry(checksumsFile);
            jos.write(checksums);
            jos.closeEntry();
        }
        temp.delete();
    }

    private void closeFiles() {
        // Close file.
        if (file != null)
            try {
                file.close();
                file = null;
            } catch (IOException e) {
                HMCLog.warn("Failed to close file", e);
            }

        // Close connection to server.
        if (stream != null)
            try {
                stream.close();
                stream = null;
            } catch (IOException e) {
                HMCLog.warn("Failed to close stream", e);
            }
    }

    @Override
    public boolean abort() {
        shouldContinue = false;
        aborted = true;
        return true;
    }

    @Override
    public String getInfo() {
        return C.i18n("download") + ": " + job.name;
    }

}
