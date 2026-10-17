/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.tukaani.xz.XZInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class LauncherLogExporter {
    private LauncherLogExporter() {
        throw new AssertionError();
    }

    public static Path exportLogsAsZip() throws IOException {
        String nameBase = "hmcl-exported-logs-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss"));
        List<Path> recentLogFiles = LOG.findRecentLogFiles(5);

        Path outputFile;
        if (recentLogFiles.isEmpty()) {
            outputFile = Metadata.CURRENT_DIRECTORY.resolve(nameBase + ".log");

            LOG.info("Exporting latest logs to " + outputFile);
            try (OutputStream output = Files.newOutputStream(outputFile)) {
                LOG.exportLogs(output);
            }
        } else {
            outputFile = Metadata.CURRENT_DIRECTORY.resolve(nameBase + ".zip");

            LOG.info("Exporting latest logs to " + outputFile);

            byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
            try (var os = Files.newOutputStream(outputFile);
                 var zos = new ZipOutputStream(os)) {

                Set<String> entryNames = new HashSet<>();

                for (Path path : recentLogFiles) {
                    String fileName = FileUtils.getName(path);
                    String extension = StringUtils.substringAfterLast(fileName, '.');

                    if ("gz".equals(extension) || "xz".equals(extension)) {
                        // If an exception occurs while decompressing the input file, we should
                        // ensure the input file and the current zip entry are closed,
                        // then copy the compressed file content as-is into a new entry in the zip file.

                        InputStream input = null;
                        try {
                            input = Files.newInputStream(path);
                            input = "gz".equals(extension)
                                    ? new GZIPInputStream(input)
                                    : new XZInputStream(input);
                        } catch (Throwable ex) {
                            LOG.warning("Failed to open log file " + path, ex);
                            IOUtils.closeQuietly(input, ex);
                            input = null;
                        }

                        String entryName = getEntryName(entryNames, StringUtils.substringBeforeLast(fileName, "."));
                        if (input != null && exportLogFile(zos, path, entryName, input, buffer))
                            continue;
                    }

                    // Copy the log file content as-is into a new entry in the zip file.
                    // If an exception occurs while decompressing the input file, we should
                    // ensure the input file and the current zip entry are closed.

                    InputStream input;
                    try {
                        input = Files.newInputStream(path);
                    } catch (Throwable ex) {
                        LOG.warning("Failed to open log file " + path, ex);
                        continue;
                    }

                    exportLogFile(zos, path, getEntryName(entryNames, fileName), input, buffer);
                }

                zos.putNextEntry(new ZipEntry(getEntryName(entryNames, "hmcl-latest.log")));
                LOG.exportLogs(zos);
                zos.closeEntry();
            }
        }

        return outputFile;
    }

    private static String getEntryName(Set<String> entryNames, String name) {
        if (entryNames.add(name)) {
            return name;
        }

        for (long i = 1; ; i++) {
            String newName = name + "." + i;
            if (entryNames.add(newName)) {
                return newName;
            }
        }
    }

    /// This method guarantees to close both `input` and the current zip entry.
    ///
    /// If no exception occurs, this method returns `true`;
    /// If an exception occurs while reading from `input`, this method returns `false`;
    /// If an exception occurs while writing to `output`, this method will throw it as is.
    private static boolean exportLogFile(ZipOutputStream output,
                                         Path file, // For logging
                                         String entryName,
                                         InputStream input,
                                         byte[] buffer) throws IOException {
        //noinspection TryFinallyCanBeTryWithResources
        try {
            output.putNextEntry(new ZipEntry(entryName));
            int read;
            while (true) {
                try {
                    read = input.read(buffer);
                    if (read <= 0)
                        return true;
                } catch (Throwable ex) {
                    LOG.warning("Failed to decompress log file " + file, ex);
                    return false;
                }

                output.write(buffer, 0, read);
            }
        } finally {
            try {
                input.close();
            } catch (Throwable ex) {
                LOG.warning("Failed to close log file " + file, ex);
            }
            output.closeEntry();
        }
    }
}
