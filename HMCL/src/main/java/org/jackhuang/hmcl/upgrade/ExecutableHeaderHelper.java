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
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jackhuang.hmcl.util.io.IOUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import static java.nio.file.StandardOpenOption.*;

/// Adds or removes the executable bootstrap header of an HMCL archive.
///
/// @author yushijinhun
@NotNullByDefault
final class ExecutableHeaderHelper {

    /// Prevents instantiation of this utility class.
    private ExecutableHeaderHelper() {}

    /// Maps supported executable suffixes to their bootstrap resources inside the JAR.
    private static final @Unmodifiable Map<String, String> suffix2header = Map.of(
            "exe", "assets/HMCLauncher.exe",
            "sh", "assets/HMCLauncher.sh"
    );

    /// Returns the case-sensitive suffix after the last dot, when the path has a file name.
    private static Optional<String> getSuffix(Path file) {
        @Nullable Path name = file.getFileName();
        if (name == null) {
            return Optional.empty();
        }
        String filename = name.toString();
        int idxDot = filename.lastIndexOf('.');
        if (idxDot < 0) {
            return Optional.empty();
        } else {
            return Optional.of(filename.substring(idxDot + 1));
        }
    }

    /// Reads the bundled executable header for a supported suffix, when present.
    private static Optional<byte @Unmodifiable []> readHeader(ZipFile zip, String suffix) throws IOException {
        @Nullable String location = suffix2header.get(suffix);
        if (location != null) {
            @Nullable ZipEntry entry = zip.getEntry(location);
            if (entry != null && !entry.isDirectory()) {
                try (InputStream in = zip.getInputStream(entry)) {
                    return Optional.of(IOUtils.readFully(in));
                }
            }
        }
        return Optional.empty();
    }

    /// Infers the suffix from the shebang or MZ prefix of an existing launcher.
    /// Unknown prefixes return no suffix; read errors propagate before the destination is truncated.
    private static Optional<String> detectHeaderSuffix(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            int first = in.read();
            int second = in.read();
            if (first == '#' && second == '!') {
                return Optional.of("sh");
            }
            if (first == 'M' && second == 'Z') {
                return Optional.of("exe");
            }
        }
        return Optional.empty();
    }

    /// Returns the exact bundled header's length at the start of the source, or zero if absent.
    private static int detectHeaderLength(ZipFile zip, FileChannel channel) throws IOException {
        ByteBuffer buf = channel.map(MapMode.READ_ONLY, 0, channel.size());
        suffixLoop: for (String suffix : suffix2header.keySet()) {
            Optional<byte @Unmodifiable []> header = readHeader(zip, suffix);
            if (header.isPresent()) {
                ((Buffer) buf).rewind();
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

    /// Copies the archive, stripping any prefix that exactly matches its bundled executable header.
    public static void copyWithoutHeader(Path from, Path to) throws IOException {
        try (
                FileChannel in = FileChannel.open(from, READ);
                FileChannel out = FileChannel.open(to, CREATE, WRITE, TRUNCATE_EXISTING);
                ZipFile zip = new ZipFile(from.toFile())
        ) {
            in.transferTo(detectHeaderLength(zip, in), Long.MAX_VALUE, out);
        }
    }

    /// Copies the archive using an explicit exe, sh, or jar destination suffix in preference to detection.
    /// Existing destinations with other suffixes are inspected to preserve a recognized executable format.
    /// The replacement header comes from the new launcher so updates also refresh the bootstrap code.
    public static void copyWithHeader(Path from, Path to) throws IOException {
        Optional<String> suffix = getSuffix(to);
        if (suffix.filter(s -> suffix2header.containsKey(s) || "jar".equals(s)).isEmpty() && Files.isRegularFile(to)) {
            // Inspect the old launcher before opening the output channel truncates it.
            suffix = detectHeaderSuffix(to);
        }

        try (
                FileChannel in = FileChannel.open(from, READ);
                FileChannel out = FileChannel.open(to, CREATE, WRITE, TRUNCATE_EXISTING);
                ZipFile zip = new ZipFile(from.toFile())
        ) {
            if (suffix.isPresent()) {
                Optional<byte @Unmodifiable []> header = readHeader(zip, suffix.get());
                if (header.isPresent()) {
                    out.write(ByteBuffer.wrap(header.get()));
                }
            }

            in.transferTo(detectHeaderLength(zip, in), Long.MAX_VALUE, out);
        }
    }
}
