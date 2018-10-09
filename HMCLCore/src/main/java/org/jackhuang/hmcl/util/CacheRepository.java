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
package org.jackhuang.hmcl.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.stream.Stream;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CacheRepository {
    private Path commonDirectory;
    private Path cacheDirectory;
    private Path indexFile;
    private Map<String, ETagItem> index;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public void changeDirectory(Path commonDir) {
        commonDirectory = commonDir;
        cacheDirectory = commonDir.resolve("cache");
        indexFile = cacheDirectory.resolve("etag.json");

        lock.writeLock().lock();
        try {
            if (Files.isRegularFile(indexFile)) {
                ETagIndex raw = JsonUtils.GSON.fromJson(FileUtils.readText(indexFile.toFile()), ETagIndex.class);
                if (raw == null)
                    index = new HashMap<>();
                else
                    index = joinETagIndexes(raw.eTag);
            } else
                index = new HashMap<>();
        } catch (IOException | JsonParseException e) {
            Logging.LOG.log(Level.WARNING, "Unable to read index file", e);
            index = new HashMap<>();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Path getCommonDirectory() {
        return commonDirectory;
    }

    public Path getCacheDirectory() {
        return cacheDirectory;
    }

    protected Path getFile(String algorithm, String hash) {
        return getCacheDirectory().resolve(algorithm).resolve(hash.substring(0, 2)).resolve(hash);
    }

    protected boolean fileExists(String algorithm, String hash) {
        if (hash == null) return false;
        Path file = getFile(algorithm, hash);
        if (Files.exists(file)) {
            try {
                return Hex.encodeHex(DigestUtils.digest(algorithm, file)).equalsIgnoreCase(hash);
            } catch (IOException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    public void tryCacheFile(Path path, String algorithm, String hash) throws IOException {
        Path cache = getFile(algorithm, hash);
        if (Files.isRegularFile(cache)) return;
        FileUtils.copyFile(path.toFile(), cache.toFile());
    }

    public Path cacheFile(Path path, String algorithm, String hash) throws IOException {
        Path cache = getFile(algorithm, hash);
        FileUtils.copyFile(path.toFile(), cache.toFile());
        return cache;
    }

    public Optional<Path> checkExistentFile(Path original, String algorithm, String hash) {
        if (fileExists(algorithm, hash))
            return Optional.of(getFile(algorithm, hash));

        if (original != null && Files.exists(original)) {
            if (hash != null) {
                try {
                    String checksum = Hex.encodeHex(DigestUtils.digest(algorithm, original));
                    if (checksum.equalsIgnoreCase(hash))
                        return Optional.of(restore(original, () -> cacheFile(original, algorithm, hash)));
                } catch (IOException e) {
                    // we cannot check the hashcode.
                }
            } else {
                return Optional.of(original);
            }
        }

        return Optional.empty();
    }

    protected Path restore(Path original, ExceptionalSupplier<Path, ? extends IOException> cacheSupplier) throws IOException {
        Path cache = cacheSupplier.get();
        Files.delete(original);
        Files.createLink(original, cache);
        return cache;
    }

    public Path getCachedRemoteFile(URLConnection conn) throws IOException {
        String url = conn.getURL().toString();
        lock.readLock().lock();
        ETagItem eTagItem;
        try {
            eTagItem = index.get(url);
        } finally {
            lock.readLock().unlock();
        }
        if (eTagItem == null) throw new IOException("Cannot find the URL");
        if (StringUtils.isBlank(eTagItem.hash) || !fileExists(SHA1, eTagItem.hash)) throw new FileNotFoundException();
        Path file = getFile(SHA1, eTagItem.hash);
        if (Files.getLastModifiedTime(file).toMillis() != eTagItem.localLastModified) {
            String hash = Hex.encodeHex(DigestUtils.digest(SHA1, file));
            if (!Objects.equals(hash, eTagItem.hash))
                throw new IOException("This file is modified");
        }
        return file;
    }

    public void injectConnection(URLConnection conn) {
        String url = conn.getURL().toString();
        lock.readLock().lock();
        ETagItem eTagItem;
        try {
            eTagItem = index.get(url);
        } finally {
            lock.readLock().unlock();
        }
        if (eTagItem == null) return;
        if (eTagItem.eTag != null)
            conn.setRequestProperty("If-None-Match", eTagItem.eTag);
        // if (eTagItem.getRemoteLastModified() != null)
        //     conn.setRequestProperty("If-Modified-Since", eTagItem.getRemoteLastModified());
    }

    public synchronized void cacheRemoteFile(Path downloaded, URLConnection conn) throws IOException {
        String eTag = conn.getHeaderField("ETag");
        if (eTag == null) return;
        String url = conn.getURL().toString();
        String lastModified = conn.getHeaderField("Last-Modified");
        String hash = Hex.encodeHex(DigestUtils.digest(SHA1, downloaded));
        Path cached = cacheFile(downloaded, SHA1, hash);
        ETagItem eTagItem = new ETagItem(url, eTag, hash, Files.getLastModifiedTime(cached).toMillis(), lastModified);
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            index.put(url, eTagItem);
            saveETagIndex();
        } finally {
            writeLock.unlock();
        }
    }

    public synchronized void cacheText(String text, URLConnection conn) throws IOException {
        String eTag = conn.getHeaderField("ETag");
        if (eTag == null) return;
        String url = conn.getURL().toString();
        String lastModified = conn.getHeaderField("Last-Modified");
        String hash = Hex.encodeHex(DigestUtils.digest(SHA1, text));
        Path cached = getFile(SHA1, hash);
        FileUtils.writeText(cached.toFile(), text);
        ETagItem eTagItem = new ETagItem(url, eTag, hash, Files.getLastModifiedTime(cached).toMillis(), lastModified);
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            index.put(url, eTagItem);
            saveETagIndex();
        } finally {
            writeLock.unlock();
        }
    }

    @SafeVarargs
    private final Map<String, ETagItem> joinETagIndexes(Collection<ETagItem>... indexes) {
        Map<String, ETagItem> eTags = new ConcurrentHashMap<>();

        Stream<ETagItem> stream = Arrays.stream(indexes).filter(Objects::nonNull).map(Collection::stream)
                .reduce(Stream.empty(), Stream::concat);

        stream.forEach(eTag -> {
            eTags.compute(eTag.url, (key, oldValue) -> {
                if (oldValue == null || oldValue.compareTo(eTag) < 0)
                    return eTag;
                else
                    return oldValue;
            });
        });

        return eTags;
    }

    public void saveETagIndex() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(indexFile.toFile(), "rw"); FileChannel channel = file.getChannel()) {
            FileLock lock = channel.lock();
            try {
                ETagIndex indexOnDisk = JsonUtils.GSON.fromJson(new String(IOUtils.readFullyWithoutClosing(Channels.newInputStream(channel)), UTF_8), ETagIndex.class);
                Map<String, ETagItem> newIndex = joinETagIndexes(indexOnDisk == null ? null : indexOnDisk.eTag, index.values());
                channel.truncate(0);
                OutputStream os = Channels.newOutputStream(channel);
                ETagIndex writeTo = new ETagIndex(newIndex.values());
                IOUtils.write(JsonUtils.GSON.toJson(writeTo).getBytes(UTF_8), os);
                this.index = newIndex;
            } finally {
                lock.release();
            }
        }
    }

    private class ETagIndex {
        private final Collection<ETagItem> eTag;

        public ETagIndex() {
            this.eTag = new HashSet<>();
        }

        public ETagIndex(Collection<ETagItem> eTags) {
            this.eTag = new HashSet<>(eTags);
        }
    }

    private class ETagItem {
        private final String url;
        private final String eTag;
        private final String hash;
        @SerializedName("local")
        private final long localLastModified;
        @SerializedName("remote")
        private final String remoteLastModified;

        /**
         * For Gson.
         */
        public ETagItem() {
            this(null, null, null, 0, null);
        }

        public ETagItem(String url, String eTag, String hash, long localLastModified, String remoteLastModified) {
            this.url = url;
            this.eTag = eTag;
            this.hash = hash;
            this.localLastModified = localLastModified;
            this.remoteLastModified = remoteLastModified;
        }

        public int compareTo(ETagItem other) {
            if (!url.equals(other.url))
                throw new IllegalArgumentException();

            ZonedDateTime thisTime = Lang.ignoringException(() -> ZonedDateTime.parse(remoteLastModified, DateTimeFormatter.RFC_1123_DATE_TIME), null);
            ZonedDateTime otherTime = Lang.ignoringException(() -> ZonedDateTime.parse(other.remoteLastModified, DateTimeFormatter.RFC_1123_DATE_TIME), null);
            if (thisTime == null && otherTime == null) return 0;
            else if (thisTime == null) return -1;
            else if (otherTime == null) return 1;
            else return thisTime.compareTo(otherTime);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ETagItem eTagItem = (ETagItem) o;
            return localLastModified == eTagItem.localLastModified &&
                    Objects.equals(url, eTagItem.url) &&
                    Objects.equals(eTag, eTagItem.eTag) &&
                    Objects.equals(hash, eTagItem.hash) &&
                    Objects.equals(remoteLastModified, eTagItem.remoteLastModified);
        }

        @Override
        public int hashCode() {
            return Objects.hash(url, eTag, hash, localLastModified, remoteLastModified);
        }
    }

    private static CacheRepository instance = new CacheRepository();

    public static CacheRepository getInstance() {
        return instance;
    }

    public static void setInstance(CacheRepository instance) {
        CacheRepository.instance = instance;
    }

    public static final String SHA1 = "SHA-1";
}
