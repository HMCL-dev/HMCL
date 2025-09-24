/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.gson.JsonUtils.*;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class CacheRepository {
    private Path commonDirectory;
    private Path cacheDirectory;
    private Path indexFile;
    private FileTime indexFileLastModified;
    private LinkedHashMap<URI, ETagItem> index;
    protected final ReadWriteLock lock = new ReentrantReadWriteLock();

    public void changeDirectory(Path commonDir) {
        commonDirectory = commonDir;
        cacheDirectory = commonDir.resolve("cache");
        indexFile = cacheDirectory.resolve("etag.json");

        lock.writeLock().lock();
        try {
            if (Files.isRegularFile(indexFile)) {
                try (FileChannel channel = FileChannel.open(indexFile, StandardOpenOption.READ);
                     @SuppressWarnings("unused") FileLock lock = channel.tryLock(0, Long.MAX_VALUE, true)) {
                    FileTime lastModified = Lang.ignoringException(() -> Files.getLastModifiedTime(indexFile));
                    ETagIndex raw = JsonUtils.GSON.fromJson(new BufferedReader(Channels.newReader(channel, UTF_8)), ETagIndex.class);
                    index = raw != null ? joinETagIndexes(raw.eTag) : new LinkedHashMap<>();
                    indexFileLastModified = lastModified;
                }
            } else {
                index = new LinkedHashMap<>();
                indexFileLastModified = null;
            }
        } catch (Exception e) {
            LOG.warning("Unable to read index file", e);
            index = new LinkedHashMap<>();
            indexFileLastModified = null;
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
        hash = hash.toLowerCase(Locale.ROOT);
        return getCacheDirectory().resolve(algorithm).resolve(hash.substring(0, 2)).resolve(hash);
    }

    protected boolean fileExists(String algorithm, String hash) {
        if (hash == null) return false;
        Path file = getFile(algorithm, hash);
        if (Files.exists(file)) {
            try {
                return DigestUtils.digestToString(algorithm, file).equalsIgnoreCase(hash);
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
        FileUtils.copyFile(path, cache);
    }

    public Path cacheFile(Path path, String algorithm, String hash) throws IOException {
        Path cache = getFile(algorithm, hash);
        FileUtils.copyFile(path, cache);
        return cache;
    }

    public Optional<Path> checkExistentFile(@Nullable Path original, String algorithm, String hash) {
        if (fileExists(algorithm, hash))
            return Optional.of(getFile(algorithm, hash));

        if (original != null && Files.exists(original)) {
            if (hash != null) {
                try {
                    String checksum = DigestUtils.digestToString(algorithm, original);
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

    public Path getCachedRemoteFile(URI uri, boolean checkExpires) throws IOException {
        lock.readLock().lock();
        ETagItem eTagItem;
        try {
            eTagItem = index.get(NetworkUtils.dropQuery(uri));
        } finally {
            lock.readLock().unlock();
        }
        if (eTagItem == null) throw new IOException("Cannot find the URL");
        if (StringUtils.isBlank(eTagItem.hash) || !fileExists(SHA1, eTagItem.hash)) throw new FileNotFoundException();
        if (checkExpires && System.currentTimeMillis() > eTagItem.expires)
            throw new CacheExpiredException(eTagItem.expires);

        Path file = getFile(SHA1, eTagItem.hash);
        if (Files.getLastModifiedTime(file).toMillis() != eTagItem.localLastModified) {
            String hash = DigestUtils.digestToString(SHA1, file);
            if (!Objects.equals(hash, eTagItem.hash))
                throw new IOException("This file is modified");
        }
        return file;
    }

    public void removeRemoteEntry(URI uri) {
        lock.writeLock().lock();
        try {
            index.remove(NetworkUtils.dropQuery(uri));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public @NotNull Map<String, String> injectConnection(URI uri) {
        try {
            uri = NetworkUtils.dropQuery(uri);
        } catch (IllegalArgumentException e) {
            return Map.of();
        }

        ETagItem eTagItem;
        lock.readLock().lock();
        try {
            eTagItem = index.get(uri);
        } finally {
            lock.readLock().unlock();
        }
        if (eTagItem == null) return Map.of();
        if (eTagItem.eTag != null)
            return Map.of("if-none-match", eTagItem.eTag);
        // if (eTagItem.getRemoteLastModified() != null)
        //     conn.setRequestProperty("If-Modified-Since", eTagItem.getRemoteLastModified());
        return Map.of();
    }

    public void injectConnection(URI uri, HttpRequest.Builder requestBuilder) {
        try {
            uri = NetworkUtils.dropQuery(uri);
        } catch (IllegalArgumentException e) {
            return;
        }

        ETagItem eTagItem;
        lock.readLock().lock();
        try {
            eTagItem = index.get(uri);
        } finally {
            lock.readLock().unlock();
        }
        if (eTagItem == null) return;
        if (eTagItem.eTag != null)
            requestBuilder.header("if-none-match", eTagItem.eTag);
        // if (eTagItem.getRemoteLastModified() != null)
        //     conn.setRequestProperty("If-Modified-Since", eTagItem.getRemoteLastModified());
    }

    public Path cacheRemoteFile(HttpResponse<?> response, Path downloaded) throws IOException {
        return cacheData(response, () -> {
            String hash = DigestUtils.digestToString(SHA1, downloaded);
            Path cached = cacheFile(downloaded, SHA1, hash);
            return new CacheResult(hash, cached);
        });
    }

    public Path cacheText(HttpResponse<?> response, String text) throws IOException {
        return cacheBytes(response, text.getBytes(UTF_8));
    }

    public Path cacheBytes(HttpResponse<?> response, byte[] bytes) throws IOException {
        return cacheData(response, () -> {
            String hash = DigestUtils.digestToString(SHA1, bytes);
            Path cached = getFile(SHA1, hash);
            Files.createDirectories(cached.getParent());
            Files.write(cached, bytes);
            return new CacheResult(hash, cached);
        });
    }

    private static final Pattern MAX_AGE = Pattern.compile("(s-maxage|max-age)=(?<time>[0-9]+)");

    private Path cacheData(HttpResponse<?> response, ExceptionalSupplier<CacheResult, IOException> cacheSupplier) throws IOException {
        String eTag = response.headers().firstValue("etag").orElse(null);
        if (StringUtils.isBlank(eTag)) return null;
        URI uri = NetworkUtils.dropQuery(response.uri());
        long expires = 0L;

        expires:
        try {
            String cacheControl = response.headers().firstValue("cache-control").orElse(null);
            if (StringUtils.isNotBlank(cacheControl)) {
                if (cacheControl.contains("no-store"))
                    return null;

                Matcher matcher = MAX_AGE.matcher(cacheControl);
                if (matcher.find()) {
                    long seconds = Long.parseLong(matcher.group("time"));
                    expires = Instant.now().plusSeconds(seconds).toEpochMilli();
                    break expires;
                }
            }

            String expiresHeader = response.headers().firstValue("expires").orElse(null);
            if (StringUtils.isNotBlank(expiresHeader)) {
                expires = ZonedDateTime.parse(expiresHeader.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli();
            }
        } catch (Throwable e) {
            LOG.warning("Failed to parse expires time", e);
        }

        String lastModified = response.headers().firstValue("last-modified").orElse(null);

        CacheResult cacheResult = cacheSupplier.get();
        ETagItem eTagItem = new ETagItem(uri.toString(),
                eTag,
                cacheResult.hash,
                Files.getLastModifiedTime(cacheResult.cachedFile).toMillis(),
                lastModified,
                expires);
        lock.writeLock().lock();
        try {
            index.compute(uri, updateEntity(eTagItem, true));
            saveETagIndex();
        } finally {
            lock.writeLock().unlock();
        }
        return cacheResult.cachedFile;
    }

    private static final class CacheResult {
        public String hash;
        public Path cachedFile;

        public CacheResult(String hash, Path cachedFile) {
            this.hash = hash;
            this.cachedFile = cachedFile;
        }
    }

    private BiFunction<URI, ETagItem, ETagItem> updateEntity(ETagItem newItem, boolean force) {
        return (key, oldItem) -> {
            if (oldItem == null) {
                return newItem;
            } else if (force || oldItem.compareTo(newItem) < 0) {
                if (!oldItem.hash.equalsIgnoreCase(newItem.hash)) {
                    Path cached = getFile(SHA1, oldItem.hash);
                    try {
                        Files.deleteIfExists(cached);
                    } catch (IOException e) {
                        LOG.warning("Cannot delete old file");
                    }
                }
                return newItem;
            } else {
                return oldItem;
            }
        };
    }

    @SafeVarargs
    private LinkedHashMap<URI, ETagItem> joinETagIndexes(Collection<ETagItem>... indexes) {
        var eTags = new LinkedHashMap<URI, ETagItem>();
        for (Collection<ETagItem> eTagItems : indexes) {
            if (eTagItems != null) {
                for (ETagItem eTag : eTagItems) {
                    eTags.compute(NetworkUtils.toURI(eTag.url), updateEntity(eTag, false));
                }
            }
        }
        return eTags;
    }

    public void saveETagIndex() throws IOException {
        try (FileChannel channel = FileChannel.open(indexFile, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
             @SuppressWarnings("unused") FileLock lock = channel.lock()) {
            FileTime lastModified = Lang.ignoringException(() -> Files.getLastModifiedTime(indexFile));
            if (indexFileLastModified == null || lastModified == null || indexFileLastModified.compareTo(lastModified) < 0) {
                try {
                    ETagIndex indexOnDisk = GSON.fromJson(
                            // Should not be closed
                            new BufferedReader(Channels.newReader(channel, UTF_8)),
                            ETagIndex.class
                    );
                    if (indexOnDisk != null) {
                        index = joinETagIndexes(index.values(), indexOnDisk.eTag);
                        indexFileLastModified = lastModified;
                    }
                } catch (JsonSyntaxException ignored) {
                }
            }

            channel.truncate(0);
            BufferedWriter writer = new BufferedWriter(Channels.newWriter(channel, UTF_8));
            JsonUtils.GSON.toJson(new ETagIndex(index.values()), writer);
            writer.flush();
            channel.force(true);

            this.indexFileLastModified = Lang.ignoringException(() -> Files.getLastModifiedTime(indexFile));
        }
    }

    private static final class ETagIndex {
        private final Collection<ETagItem> eTag;

        public ETagIndex() {
            this.eTag = new HashSet<>();
        }

        public ETagIndex(Collection<ETagItem> eTags) {
            this.eTag = new HashSet<>(eTags);
        }
    }

    private static final class ETagItem {
        private final String url;
        private final String eTag;
        private final String hash;
        @SerializedName("local")
        private final long localLastModified;
        @SerializedName("remote")
        private final String remoteLastModified;
        private final long expires;

        /**
         * For Gson.
         */
        public ETagItem() {
            this(null, null, null, 0, null, 0L);
        }

        public ETagItem(String url, String eTag, String hash, long localLastModified, String remoteLastModified, long expires) {
            this.url = url;
            this.eTag = eTag;
            this.hash = hash;
            this.localLastModified = localLastModified;
            this.remoteLastModified = remoteLastModified;
            this.expires = expires;
        }

        public long getExpires() {
            return expires;
        }

        public int compareTo(ETagItem other) {
            if (!url.equals(other.url) && !NetworkUtils.toURI(url).equals(NetworkUtils.toURI(other.url)))
                throw new IllegalArgumentException();

            ZonedDateTime thisTime = Lang.ignoringException(() -> ZonedDateTime.parse(remoteLastModified, DateTimeFormatter.RFC_1123_DATE_TIME), null);
            ZonedDateTime otherTime = Lang.ignoringException(() -> ZonedDateTime.parse(other.remoteLastModified, DateTimeFormatter.RFC_1123_DATE_TIME), null);
            if (thisTime == null && otherTime == null) return 0;
            else if (thisTime == null) return 1;
            else if (otherTime == null) return -1;
            else return thisTime.compareTo(otherTime);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof ETagItem that
                    && localLastModified == that.localLastModified
                    && Objects.equals(url, that.url)
                    && Objects.equals(eTag, that.eTag)
                    && Objects.equals(hash, that.hash)
                    && Objects.equals(remoteLastModified, that.remoteLastModified)
                    && this.expires == that.expires;
        }

        @Override
        public int hashCode() {
            return Objects.hash(url, eTag, hash, localLastModified, remoteLastModified, expires);
        }

        @Override
        public String toString() {
            return "ETagItem[" +
                    "url='" + url + '\'' +
                    ", eTag='" + eTag + '\'' +
                    ", hash='" + hash + '\'' +
                    ", localLastModified=" + localLastModified +
                    ", remoteLastModified='" + remoteLastModified + '\'' +
                    ", expires=" + expires +
                    ']';
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

    public static class CacheExpiredException extends IOException {
        private final long expires;

        public CacheExpiredException(long expires) {
            this.expires = expires;
        }

        public CacheExpiredException(String message, long expires) {
            super(message);
            this.expires = expires;
        }

        public long getExpires() {
            return expires;
        }
    }
}
