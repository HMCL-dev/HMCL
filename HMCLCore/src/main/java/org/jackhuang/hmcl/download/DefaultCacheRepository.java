// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.LibraryDownloadInfo;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.TolerableValidationException;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.Lock;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Caches verified library files and resolves cached or legacy library locations.
public class DefaultCacheRepository extends CacheRepository {
    private Path librariesDir;
    private Path indexFile;
    private Index index = null;

    public DefaultCacheRepository() {
        this(OperatingSystem.getWorkingDirectory("minecraft"));
    }

    public DefaultCacheRepository(Path commonDirectory) {
        changeDirectory(commonDirectory);
    }

    @Override
    public void changeDirectory(Path commonDir) {
        super.changeDirectory(commonDir);

        librariesDir = commonDir.resolve("libraries");
        indexFile = getCacheDirectory().resolve("index.json");

        lock.writeLock().lock();
        try {
            if (Files.isRegularFile(indexFile)) {
                index = JsonUtils.fromJsonFile(indexFile, Index.class);
                if (index == null) {
                    throw new JsonParseException("Index file is empty or invalid");
                }
            } else {
                index = new Index();
            }
        } catch (Exception e) {
            LOG.warning("Unable to read index file", e);
            index = new Index();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /// Caches a library only when its download SHA-1 or nonempty legacy checksum list verifies the file.
    /// Already indexed libraries are skipped; I/O failures are logged without propagating.
    ///
    /// @param library the library metadata used for validation
    /// @param jar the local file to verify and cache
    public void tryCacheLibrary(Library library, Path jar) {
        lock.readLock().lock();
        try {
            if (index.libraries().stream().anyMatch(it -> library.name().equals(it.name())))
                return;
        } finally {
            lock.readLock().unlock();
        }

        try {
            LibraryDownloadInfo info = library.getDownload();
            @Nullable String hash = info.getSha1();
            if (hash != null) {
                String checksum = DigestUtils.digestToString("SHA-1", jar);
                if (hash.equalsIgnoreCase(checksum))
                    cacheLibrary(library, jar, false);
            } else if (library.checksums() != null && !library.checksums().isEmpty()
                    && library.validateChecksums(jar)) {
                cacheLibrary(library, jar, true);
            }
        } catch (IOException e) {
            LOG.warning("Unable to calc hash value of file " + jar, e);
        }
    }

    /// Finds a verified cached library, falling back to the legacy common-directory layout.
    /// Legacy files without checksum metadata remain usable without being added to the cache.
    ///
    /// @param library the library to locate
    /// @return its cached or legacy path, or empty when no usable file is found
    public Optional<Path> getLibrary(Library library) {
        LibraryDownloadInfo info = library.getDownload();
        @Nullable String hash = info.getSha1();

        if (fileExists(SHA1, hash))
            return Optional.of(getFile(SHA1, hash));

        Lock readLock = lock.readLock();
        readLock.lock();

        try {
            // check if this library is from Forge
            List<LibraryIndex> libraries = index.libraries().stream()
                    .filter(it -> it.name().equals(library.name()))
                    .toList();
            for (LibraryIndex libIndex : libraries) {
                if (fileExists(SHA1, libIndex.hash())) {
                    Path file = getFile(SHA1, libIndex.hash());
                    if (libIndex.type().equalsIgnoreCase(LibraryIndex.TYPE_FORGE)) {
                        if (library.validateChecksums(file))
                            return Optional.of(file);
                    }
                }
            }
        } finally {
            readLock.unlock();
        }

        // check old common directory
        Path jar = librariesDir.resolve(info.getPath());
        if (Files.exists(jar)) {
            try {
                if (hash != null) {
                    String checksum = DigestUtils.digestToString("SHA-1", jar);
                    if (hash.equalsIgnoreCase(checksum))
                        return Optional.of(restore(jar, () -> cacheLibrary(library, jar, false)));
                } else {
                    if (library.checksums() != null && !library.checksums().isEmpty()) {
                        if (library.validateChecksums(jar))
                            return Optional.of(restore(jar, () -> cacheLibrary(library, jar, true)));
                    } else {
                        return Optional.of(jar);
                    }
                }
            } catch (IOException e) {
                // we cannot check the hashcode or unable to move file.
            }
        }

        return Optional.empty();
    }

    /**
     * Caches the library file to repository.
     *
     * @param library the library to cache
     * @param path    the file being cached, must be verified
     * @param forge   true if this library is provided by Forge
     * @return cached file location
     * @throws IOException if failed to calculate hash code of {@code path} or copy the file to cache
     */
    public Path cacheLibrary(Library library, Path path, boolean forge) throws IOException {
        String hash = library.getDownload().getSha1();
        if (!DigestUtils.isSha1Digest(hash))
            hash = DigestUtils.digestToString(SHA1, path);

        Path cache = getFile(SHA1, hash);
        FileUtils.copyFile(path, cache);

        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            LibraryIndex libIndex = new LibraryIndex(library.name(), hash, forge ? LibraryIndex.TYPE_FORGE : LibraryIndex.TYPE_JAR);
            index.libraries().add(libIndex);
            saveIndex();
        } finally {
            writeLock.unlock();
        }

        return cache;
    }

    private void saveIndex() {
        if (indexFile == null || index == null) return;
        try {
            Files.createDirectories(indexFile.getParent());
            FileUtils.saveSafely(indexFile, outputStream -> {
                try (var writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                    JsonUtils.GSON.toJson(index, writer);
                }
            });
        } catch (IOException e) {
            LOG.error("Unable to save index.json", e);
        }
    }

    /// ```json
    /// {
    ///     "libraries": {
    ///         // allow a library has multiple hash code.
    ///         [
    ///             "name": "net.minecraftforge:forge:1.11.2-13.20.0.2345",
    ///             "hash": "blablabla",
    ///             "type": "forge"
    ///         ]
    ///     }
    /// }
    /// ```
    /// assets and versions will not be included in index.
    private record Index(@NotNull Set<LibraryIndex> libraries) implements Validation {
        public Index() {
            this(new HashSet<>());
        }

        private Index(Set<LibraryIndex> libraries) {
            this.libraries = Objects.requireNonNull(libraries);
        }

        @Override
        public void validate() throws JsonParseException, TolerableValidationException {
            if (libraries == null)
                throw new JsonParseException("Index.libraries cannot be null");
        }
    }

    private record LibraryIndex(@NotNull String name, @NotNull String hash, @NotNull String type) implements Validation {
        public LibraryIndex() {
            this("", "", "");
        }

        @Override
        public void validate() throws JsonParseException, TolerableValidationException {
            if (name == null || hash == null || type == null)
                throw new JsonParseException("Index.LibraryIndex.* cannot be null");
        }

        public static final String TYPE_FORGE = "forge";
        public static final String TYPE_JAR = "jar";
    }
}
