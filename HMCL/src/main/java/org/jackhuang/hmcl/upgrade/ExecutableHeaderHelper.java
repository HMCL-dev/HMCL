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
package org.jackhuang.hmcl.upgrade;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;
import kala.compress.utils.SeekableInMemoryByteChannel;

import static java.nio.file.StandardOpenOption.*;

/**
 * Helper class for adding/removing executable header from HMCL file.
 *
 * @author yushijinhun
 */
final class ExecutableHeaderHelper {
    private ExecutableHeaderHelper() {
    }

    private static Optional<String> getSuffix(Path file) {
        String filename = file.getFileName().toString();
        int idxDot = filename.lastIndexOf('.');
        return idxDot >= 0
                ? Optional.of(filename.substring(idxDot + 1))
                : Optional.empty();
    }

    private static Optional<byte[]> readHeader(ZipArchiveReader zip, String suffix) throws IOException {
        String location = "assets/HMCLauncher." + suffix;
        ZipArchiveEntry entry = zip.getEntry(location);
        if (entry != null && !entry.isDirectory()) {
            try (InputStream in = zip.getInputStream(entry)) {
                return Optional.of(in.readAllBytes());
            }
        }
        return Optional.empty();
    }

    /**
     * Copies the executable and appends the header according to the suffix.
     */
    public static void copyWithHeader(Path from, Path to) throws IOException {
        byte[] source = Files.readAllBytes(from);

        Files.createDirectories(to.toAbsolutePath().normalize().getParent());
        try (var reader = new ZipArchiveReader(new SeekableInMemoryByteChannel(source));
             var output = Files.newOutputStream(to, CREATE, WRITE, TRUNCATE_EXISTING)) {
            Optional<String> suffix = getSuffix(to);
            if (suffix.isPresent()) {
                Optional<byte[]> header = readHeader(reader, suffix.get());
                if (header.isPresent()) {
                    output.write(header.get());
                }
            }

            final int offset = Math.toIntExact(reader.getFirstLocalFileHeaderOffset());
            output.write(source, offset, source.length - offset);
        }
    }
}
