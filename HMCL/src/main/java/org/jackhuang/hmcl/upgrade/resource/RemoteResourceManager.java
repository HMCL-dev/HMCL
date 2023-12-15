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
package org.jackhuang.hmcl.upgrade.resource;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.versions.ModTranslations;
import org.jackhuang.hmcl.upgrade.hmcl.IntegrityChecker;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class RemoteResourceManager {
    private RemoteResourceManager() {
    }

    private static final class RemoteResource {
        @SerializedName("sha1")
        private final String sha1;

        @SerializedName("urls")
        private final String[] urls;

        private transient byte[] data = null;

        private RemoteResource(String sha1, String[] urls) {
            this.sha1 = sha1;
            this.urls = urls;
        }

        public void download(Path path, Runnable callback) {
            if (data != null) {
                return;
            }

            new FileDownloadTask(Arrays.stream(urls).map(NetworkUtils::toURL).collect(Collectors.toList()), path.toFile(), new FileDownloadTask.IntegrityCheck("SHA-1", sha1))
                    .whenComplete(Schedulers.defaultScheduler(), (result, exception) -> {
                        if (exception != null) {
                            data = Files.readAllBytes(path);
                            callback.run();
                        }
                    }).start();
        }
    }

    public static final class RemoteResourceKey {
        private final String namespace;
        private final String name;
        private final String version;
        private final Path cachePath;
        private final ExceptionalSupplier<InputStream, IOException> localResourceSupplier;
        private String localResourceSha1 = null;

        public RemoteResourceKey(String namespace, String name, String version, ExceptionalSupplier<InputStream, IOException> localResourceSupplier) {
            this.namespace = namespace;
            this.name = name;
            this.version = version;
            this.localResourceSupplier = localResourceSupplier;

            this.cachePath = Metadata.HMCL_DIRECTORY.resolve("remoteResources").resolve(namespace).resolve(name).resolve(version).resolve(String.format("%s-%s-%s.resource", namespace, name, version));
        }

        private InputStream getLocalResource() throws IOException {
            if (Files.isReadable(cachePath)) {
                return Files.newInputStream(cachePath);
            }
            return localResourceSupplier.get();
        }

        private String getLocalResourceSha1() throws IOException {
            if (localResourceSha1 == null) {
                localResourceSha1 = DigestUtils.digestToString("SHA-1", IOUtils.readFullyAsByteArray(getLocalResource()));
            }

            return localResourceSha1;
        }

        @Nullable
        private RemoteResource getRemoteResource() {
            return Optional.ofNullable(remoteResources.get(namespace)).map(map -> map.get(name)).map(map -> map.get(version)).orElse(null);
        }

        @Nullable
        public InputStream getResource() throws IOException {
            RemoteResource remoteResource = getRemoteResource();

            if (remoteResource == null) {
                return getLocalResource();
            }

            if (remoteResource.sha1.equals(getLocalResourceSha1())) {
                return getLocalResource();
            }

            if (remoteResource.data == null) {
                return null;
            }

            return new ByteArrayInputStream(remoteResource.data);
        }

        public void downloadRemoteResourceIfNecessary() throws IOException {
            RemoteResource remoteResource = getRemoteResource();

            if (remoteResource == null) {
                return;
            }

            if (remoteResource.sha1.equals(getLocalResourceSha1())) {
                return;
            }

            remoteResource.download(cachePath, () -> localResourceSha1 = null);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RemoteResourceKey that = (RemoteResourceKey) o;

            if (!namespace.equals(that.namespace)) return false;
            if (!name.equals(that.name)) return false;
            return version.equals(that.version);
        }

        @Override
        public int hashCode() {
            int result = namespace.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + version.hashCode();
            return result;
        }
    }

    private static final Map<String, Map<String, Map<String, RemoteResource>>> remoteResources = new ConcurrentHashMap<>();

    private static final Map<String, RemoteResourceKey> keys = new ConcurrentHashMap<>();

    public static void init() {
        Task.<Map<String, Map<String, Map<String, RemoteResource>>>>supplyAsync(() ->
                IntegrityChecker.isSelfVerified() ? HttpRequest.GET(Metadata.RESOURCE_UPDATE_URL).getJson(
                        new TypeToken<Map<String, Map<String, Map<String, RemoteResource>>>>() {
                        }.getType()
                ) : null
        ).whenComplete(Schedulers.defaultScheduler(), (result, exception) -> {
            if (exception == null && result != null) {
                remoteResources.clear();
                remoteResources.putAll(result);

                for (RemoteResourceKey key : keys.values()) {
                    key.downloadRemoteResourceIfNecessary();
                }
            }
        }).start();
    }

    public static void register() {
        ModTranslations.values();
    }

    public static RemoteResourceKey get(@NotNull String namespace, @NotNull String name, @NotNull String version, ExceptionalSupplier<InputStream, IOException> defaultSupplier) {
        String stringKey = String.format("%s:%s:%s", namespace, name, version);
        RemoteResourceKey key = keys.containsKey(stringKey) ? keys.get(stringKey) : new RemoteResourceKey(namespace, name, version, defaultSupplier);
        Task.runAsync(key::downloadRemoteResourceIfNecessary).start();
        keys.put(stringKey, key);
        return key;
    }
}
