/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.game;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jackhuang.hmcl.download.game.LibraryDownloadTask;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class HMCLLocalRepository {
    private final StringProperty directory = new SimpleStringProperty();

    private Path cacheDir;
    private Path librariesDir;
    private Path jarsDir;
    private Path indexFile;

    private Index index = null;

    public HMCLLocalRepository() {
        FXUtils.onChange(directory, t -> changeDirectory(Paths.get(t)));
    }

    public String getDirectory() {
        return directory.get();
    }

    public StringProperty directoryProperty() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory.set(directory);
    }

    private void changeDirectory(Path commonDir) {
        cacheDir = commonDir.resolve("cache");
        librariesDir = commonDir.resolve("libraries");
        jarsDir = commonDir.resolve("jars");
        indexFile = cacheDir.resolve("index.json");

        try {
            index = Constants.GSON.fromJson(FileUtils.readText(indexFile.toFile()), Index.class);
        } catch (IOException e) {
            Logging.LOG.log(Level.WARNING, "Unable to read index file", e);
            index = new Index();
        }
    }

    private Path getFile(String algorithm, String hash) {
        return cacheDir.resolve(algorithm).resolve(hash.substring(0, 2)).resolve(hash);
    }

    private boolean fileExists(String algorithm, String hash) {
        if (hash == null) return false;
        return Files.exists(getFile(algorithm, hash));
    }

    public void tryCacheLibrary(Library library, Path jar) {
        if (index.getLibraries().stream().anyMatch(it -> library.getName().equals(it.getName())))
            return;

        try {
            LibraryDownloadInfo info = library.getDownload();
            String hash = info.getSha1();
            if (hash != null) {
                String checksum = Hex.encodeHex(DigestUtils.digest("SHA-1", jar));
                if (hash.equalsIgnoreCase(checksum))
                    cacheLibrary(library, jar, false);
            } else if (library.getChecksums() != null && !library.getChecksums().isEmpty()) {
                if (LibraryDownloadTask.checksumValid(jar.toFile(), library.getChecksums()))
                    cacheLibrary(library, jar, true);
            } else {
                // or we will not cache the library
            }
        } catch (IOException e) {
            Logging.LOG.log(Level.WARNING, "Unable to calc hash value of file " + jar, e);
        }
    }

    public synchronized Optional<Path> getLibrary(Library library) {
        LibraryDownloadInfo info = library.getDownload();
        String hash = info.getSha1();

        if (fileExists(SHA1, hash))
            return Optional.of(getFile(SHA1, hash));

        // check if this library is from Forge
        List<LibraryIndex> libraries = index.getLibraries().stream()
                .filter(it -> it.getName().equals(library.getName()))
                .collect(Collectors.toList());
        for (LibraryIndex libIndex : libraries) {
            if (fileExists(SHA1, libIndex.getHash())) {
                Path file = getFile(SHA1, libIndex.getHash());
                if (libIndex.getType().equalsIgnoreCase(LibraryIndex.TYPE_FORGE)) {
                    if (LibraryDownloadTask.checksumValid(file.toFile(), library.getChecksums()))
                        return Optional.of(file);
                }
            }
        }

        // check old common directory
        Path jar = librariesDir.resolve(info.getPath());
        if (Files.exists(jar)) {
            try {
                if (hash != null) {
                    String checksum = Hex.encodeHex(DigestUtils.digest("SHA-1", jar));
                    if (hash.equalsIgnoreCase(checksum))
                        return Optional.of(restore(jar, () -> cacheLibrary(library, jar, false)));
                } else if (library.getChecksums() != null && !library.getChecksums().isEmpty()) {
                    if (LibraryDownloadTask.checksumValid(jar.toFile(), library.getChecksums()))
                        return Optional.of(restore(jar, () -> cacheLibrary(library, jar, true)));
                } else {
                    return Optional.of(jar);
                }
            } catch (IOException e) {
                // we cannot check the hashcode or unable to move file.
            }
        }

        return Optional.empty();
    }

    public synchronized Path cacheLibrary(Library library, Path path, boolean forge) throws IOException {
        String hash = library.getDownload().getSha1();
        if (hash == null)
            hash = Hex.encodeHex(DigestUtils.digest(SHA1, path));

        Path cache = getFile(SHA1, hash);
        FileUtils.copyFile(path.toFile(), cache.toFile());

        LibraryIndex libIndex = new LibraryIndex(library.getName(), hash, forge ? LibraryIndex.TYPE_FORGE : LibraryIndex.TYPE_JAR);
        index.getLibraries().add(libIndex);
        saveIndex();

        return cache;
    }

    public synchronized Optional<Path> getVersion(String gameVersion, Version version) {
        DownloadInfo info = version.getDownloadInfo();
        String hash = info.getSha1();

        if (fileExists(SHA1, hash))
            return Optional.of(getFile(SHA1, hash));

        // check old common directory, but we will no longer maintain it.
        Path jar = jarsDir.resolve(gameVersion + ".jar");
        if (Files.exists(jar)) {
            if (hash != null) {
                try {
                    String checksum = Hex.encodeHex(DigestUtils.digest("SHA-1", jar));
                    if (!checksum.equalsIgnoreCase(hash)) {
                        // The file is not the one we want
                        return Optional.empty();
                    } else {
                        return Optional.of(restore(jar, () -> cacheVersion(version, jar)));
                    }
                } catch (IOException e) {
                    // we cannot check the hashcode.
                    return Optional.empty();
                }
            } else {
                return Optional.of(jar);
            }
        }

        return Optional.empty();
    }

    public synchronized Path cacheVersion(Version version, Path path) throws IOException {
        if (version.getDownloadInfo().getSha1() == null)
            throw new IllegalStateException();

        Path cache = getFile(SHA1, version.getDownloadInfo().getSha1());
        FileUtils.copyFile(path.toFile(), cache.toFile());
        return cache;
    }

    private Path restore(Path original, ExceptionalSupplier<Path, ? extends IOException> cacheSupplier) throws IOException {
        Path cache = cacheSupplier.get();
        Files.delete(original);
        Files.createLink(original, cache);
        return cache;
    }

    private void saveIndex() {
        if (indexFile == null || index == null) return;
        try {
            FileUtils.writeText(indexFile.toFile(), Constants.GSON.toJson(index));
        } catch (IOException e) {
            Logging.LOG.log(Level.SEVERE, "Unable to save index.json", e);
        }
    }

    private static final String SHA1 = "SHA-1";
    public static final HMCLLocalRepository REPOSITORY = new HMCLLocalRepository();

    /**
     * {
     *     "libraries": {
     *         // allow a library has multiple hash code.
     *         [
     *             "name": "net.minecraftforge:forge:1.11.2-13.20.0.2345",
     *             "hash": "blablabla",
     *             "type": "forge"
     *         ]
     *     },
     *     "indexes": [
     *         {
     *             "name": "1.7.10",
     *             "hash": "..."
     *         }
     *     ]
     *     // we don't cache asset objects in our repository which are already stored in a cache repository.
     * }
     */
    private class Index {
        private final Set<LibraryIndex> libraries;

        public Index() {
            this(new HashSet<>());
        }

        public Index(Set<LibraryIndex> libraries) {
            this.libraries = libraries;
        }

        public Set<LibraryIndex> getLibraries() {
            return libraries;
        }
    }

    private class LibraryIndex {
        private final String name;
        private final String hash;
        private final String type;

        public LibraryIndex() {
            this(null, null, null);
        }

        public LibraryIndex(String name, String hash, String type) {
            this.name = name;
            this.hash = hash;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getHash() {
            return hash;
        }

        public String getType() {
            return type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LibraryIndex that = (LibraryIndex) o;
            return Objects.equals(name, that.name) &&
                    Objects.equals(hash, that.hash) &&
                    Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, hash, type);
        }

        public static final String TYPE_FORGE = "forge";
        public static final String TYPE_JAR = "jar";
    }
}
