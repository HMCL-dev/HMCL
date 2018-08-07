/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.upgrade;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jackhuang.hmcl.util.IOUtils;

/**
 * Helper class for adding/removing executable header from HMCL file.
 *
 * @author yushijinhun
 */
final class ExecutableHeaderHelper {
    private ExecutableHeaderHelper() {}

    private static Map<String, String> suffix2header = mapOf(
            pair("exe", "assets/HMCLauncher.exe"));

    private static Optional<String> getSuffix(Path file) {
        String filename = file.getFileName().toString();
        int idxDot = filename.lastIndexOf('.');
        if (idxDot < 0) {
            return Optional.empty();
        } else {
            return Optional.of(filename.substring(idxDot + 1));
        }
    }

    private static Optional<byte[]> readHeader(ZipFile zip, String suffix) throws IOException {
        String location = suffix2header.get(suffix);
        if (location != null) {
            ZipEntry entry = zip.getEntry(location);
            if (entry != null && !entry.isDirectory()) {
                try (InputStream in = zip.getInputStream(entry)) {
                    return Optional.of(IOUtils.readFullyAsByteArray(in));
                }
            }
        }
        return Optional.empty();
    }

    private static int detectHeaderLength(ZipFile zip, FileChannel channel) throws IOException {
        ByteBuffer buf = channel.map(MapMode.READ_ONLY, 0, channel.size());
        suffixLoop: for (String suffix : suffix2header.keySet()) {
            Optional<byte[]> header = readHeader(zip, suffix);
            if (header.isPresent()) {
                buf.rewind();
                for (byte b : header.get()) {
                    if (!buf.hasRemaining() || b != buf.get()) {
                        continue suffixLoop;
                    }
                }
                return header.get().length;
            }
        }
        return 0;
    }

    /**
     * Copies the executable and removes its header.
     */
    public static void copyWithoutHeader(Path from, Path to) throws IOException {
        try (
                FileChannel in = FileChannel.open(from, READ);
                FileChannel out = FileChannel.open(to, CREATE, WRITE, TRUNCATE_EXISTING);
                ZipFile zip = new ZipFile(from.toFile())
        ) {
            in.transferTo(detectHeaderLength(zip, in), Long.MAX_VALUE, out);
        }
    }

    /**
     * Copies the executable and appends the header according to the suffix.
     */
    public static void copyWithHeader(Path from, Path to) throws IOException {
        try (
                FileChannel in = FileChannel.open(from, READ);
                FileChannel out = FileChannel.open(to, CREATE, WRITE, TRUNCATE_EXISTING);
                ZipFile zip = new ZipFile(from.toFile())
        ) {
            Optional<String> suffix = getSuffix(to);
            if (suffix.isPresent()) {
                Optional<byte[]> header = readHeader(zip, suffix.get());
                if (header.isPresent()) {
                    out.write(ByteBuffer.wrap(header.get()));
                }
            }

            in.transferTo(detectHeaderLength(zip, in), Long.MAX_VALUE, out);
        }
    }
}
