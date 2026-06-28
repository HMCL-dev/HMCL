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
package org.jackhuang.hmcl.theme;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/// A readable resource contributed by a theme pack.
@NotNullByDefault
public sealed interface ThemePackResource
        permits ThemePackResource.File, ThemePackResource.Zip, ThemePackResource.Builtin {
    /// Returns a stable resource name used for display and extension detection.
    ///
    /// @return the resource name
    String name();

    /// Opens a fresh input stream for this resource.
    ///
    /// @return a new stream for reading the resource content
    /// @throws IOException if the resource cannot be opened
    InputStream openStream() throws IOException;

    /// Returns the backing file when this resource is a regular filesystem file.
    ///
    /// @return the backing file, or `null` when the resource is not a direct file
    default @Nullable Path file() {
        return null;
    }

    /// A resource stored as a regular filesystem file.
    ///
    /// @param path the resource file path
    /// @param name the stable resource name
    @NotNullByDefault
    record File(Path path, String name) implements ThemePackResource {
        /// Creates a file-backed theme-pack resource named after its file name.
        ///
        /// @param path the resource file path
        public File(Path path) {
            this(path, Objects.requireNonNull(path).getFileName().toString());
        }

        /// Creates a file-backed theme-pack resource.
        ///
        /// @param path the resource file path
        /// @param name the stable resource name
        public File {
            path = Objects.requireNonNull(path).toAbsolutePath().normalize();
            name = Objects.requireNonNull(name).trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Theme-pack resource name is blank");
            }
        }

        /// Opens the file.
        @Override
        public InputStream openStream() throws IOException {
            return Files.newInputStream(path);
        }

        /// Returns the backing file.
        @Override
        public Path file() {
            return path;
        }
    }

    /// A resource stored as an entry inside a zip theme-pack file.
    ///
    /// @param zipFile   the zip theme-pack file
    /// @param entryName the normalized zip entry name
    @NotNullByDefault
    record Zip(Path zipFile, String entryName) implements ThemePackResource {
        /// Creates a zip-entry theme-pack resource.
        ///
        /// @param zipFile   the zip theme-pack file
        /// @param entryName the normalized zip entry name
        public Zip {
            zipFile = Objects.requireNonNull(zipFile).toAbsolutePath().normalize();
            entryName = ThemePackAsset.normalizeEntryName(entryName);
        }

        /// Returns the zip entry name.
        @Override
        public String name() {
            return entryName;
        }

        /// Opens the zip entry and closes the zip file when the returned stream is closed.
        @Override
        public InputStream openStream() throws IOException {
            ZipFile zip = new ZipFile(zipFile.toFile(), StandardCharsets.UTF_8);
            boolean success = false;
            try {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null || entry.isDirectory()) {
                    throw new IOException("Theme-pack asset is missing: " + entryName);
                }
                InputStream input = zip.getInputStream(entry);
                success = true;
                return new ZipEntryInputStream(input, zip);
            } finally {
                if (!success) {
                    zip.close();
                }
            }
        }
    }

    /// A resource stored in launcher-bundled classpath resources.
    ///
    /// @param resourcePath the classpath resource path
    /// @param entryName    the theme-pack entry name
    @NotNullByDefault
    record Builtin(String resourcePath, String entryName) implements ThemePackResource {
        /// Creates a classpath-backed theme-pack resource.
        ///
        /// @param resourcePath the classpath resource path
        /// @param entryName    the theme-pack entry name
        public Builtin {
            resourcePath = Objects.requireNonNull(resourcePath);
            entryName = ThemePackAsset.normalizeEntryName(entryName);
        }

        /// Returns the theme-pack entry name.
        @Override
        public String name() {
            return entryName;
        }

        /// Opens the classpath resource.
        @Override
        public InputStream openStream() throws IOException {
            @Nullable InputStream input = ThemePackResource.class.getResourceAsStream(resourcePath);
            if (input == null) {
                throw new IOException("Built-in theme-pack asset is missing: " + entryName);
            }
            return input;
        }
    }

    /// An input stream that owns a zip file handle.
    @NotNullByDefault
    final class ZipEntryInputStream extends FilterInputStream {
        /// The zip file that must stay open while this stream is read.
        private final ZipFile zipFile;

        /// Whether this stream has already been closed.
        private boolean closed;

        /// Creates a zip-entry stream.
        ///
        /// @param input   the zip entry input stream
        /// @param zipFile the owning zip file
        private ZipEntryInputStream(InputStream input, ZipFile zipFile) {
            super(input);
            this.zipFile = Objects.requireNonNull(zipFile);
        }

        /// Closes both the entry stream and the owning zip file.
        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;

            @Nullable IOException exception = null;
            try {
                super.close();
            } catch (IOException e) {
                exception = e;
            }

            try {
                zipFile.close();
            } catch (IOException e) {
                if (exception != null) {
                    exception.addSuppressed(e);
                } else {
                    exception = e;
                }
            }

            if (exception != null) {
                throw exception;
            }
        }
    }
}
