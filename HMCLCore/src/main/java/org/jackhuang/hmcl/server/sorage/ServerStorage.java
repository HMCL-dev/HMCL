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
package org.jackhuang.hmcl.server.sorage;

import org.jackhuang.hmcl.game.GameInstance;
import org.jackhuang.hmcl.server.Server;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ServerStorage {
    private final Lock handleLock = new ReentrantLock();
    private final List<GameInstance> holdInstances = new CopyOnWriteArrayList<>();
    private final List<ServerStorageEntry> storageEntries = new CopyOnWriteArrayList<>();
    private final Path serverDatFilePath;
    private static volatile HashCalcer hashCalcer;
    private volatile boolean storageValid = true;
    private byte[] currentFileHash = new byte[0];

    public ServerStorage(Path serverDatFilePath) throws IOException {
        this.serverDatFilePath = serverDatFilePath;

        handleLock.lock();
        try {
            storageEntries.clear();
            if (Files.exists(serverDatFilePath)) {
                byte[] bytes = Files.readAllBytes(serverDatFilePath);
                this.currentFileHash = calcServerDatFileHash(bytes);
                List<Server> servers = Server.loadFromServersDat(bytes);
                for (Server server : servers) {
                    ServerStorageEntry entry = new ServerStorageEntry(server);
                    storageEntries.add(entry);
                }
            }
        } finally {
            handleLock.unlock();
        }
    }

    private HashCalcer createMd5Calcer() {
        try {
            MessageDigest md5MessageDigest = MessageDigest.getInstance("MD5");
            return input -> {
                    byte[] hash;
                    synchronized (md5MessageDigest) {
                        hash = md5MessageDigest.digest(input);
                    }
                    return hash;
            };
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Failed to create md5 calcer.", e);
            return bytes -> new byte[0];
        }
    }

    private byte[] calcServerDatFileHash(byte[] data) throws IOException {
        if (hashCalcer == null) {
            synchronized (ServerStorage.class) {
                if (hashCalcer == null) {
                    hashCalcer = createMd5Calcer();
                }
            }
        }
        return hashCalcer.calc(data);
    }

    public List<GameInstance> getHoldInstances() {
        return holdInstances;
    }

    public List<ServerStorageEntry> getStorageEntries() {
        return storageEntries;
    }

    private void save() throws IOException {
        handleLock.lock();
        try {
            if (!storageValid) return;

            checkHash();

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                Server.saveServersDat(storageEntries.stream().map(e -> e.server).toList(), outputStream);
                byte[] bytes = outputStream.toByteArray();

                FileUtils.saveSafely(serverDatFilePath, op ->
                        op.write(bytes)
                );

                currentFileHash = calcServerDatFileHash(bytes);
            }

        } finally {
            handleLock.unlock();
        }
    }

    public void invalid() {
        handleLock.lock();
        try {
            storageValid = false;
        } finally {
            handleLock.unlock();
        }
    }

    private void deleteEntry(ServerStorageEntry entry) throws IOException {
        handleLock.lock();
        try {
            if (!storageValid) return;

            int index = storageEntries.indexOf(entry);
            if (index != -1) {
                ServerStorageEntry removed = storageEntries.remove(index);
                try {
                    save();
                } catch (IOException e) {
                    storageEntries.add(index, removed);
                    throw e;
                }
            }
        } finally {
            handleLock.unlock();
        }
    }

    public boolean updateEntry(ServerStorageEntry entry, UnaryOperator<@NotNull Server> updater) throws IOException {
        handleLock.lock();
        try {
            if (!storageValid) return false;

            int index = storageEntries.indexOf(entry);
            if (index == -1) return false;

            Server oldValue = entry.server;
            Server newValue = updater.apply(oldValue);

            if (oldValue.equals(newValue)) return false;

            try {
                entry.server = newValue;
                save();
            } catch (IOException e) {
                entry.server = oldValue;
                throw e;
            }

            return true;
        } finally {
            handleLock.unlock();
        }
    }

    public @Nullable ServerStorageEntry add(Server server) throws IOException {
        handleLock.lock();
        try {
            if (!storageValid) return null;
            ServerStorageEntry entry = new ServerStorageEntry(server);

            try {
                storageEntries.add(entry);
                save();
            } catch (IOException e) {
                storageEntries.remove(entry);
                throw e;
            }

            return entry;
        } finally {
            handleLock.unlock();
        }
    }

    public void checkHash() throws IOException {
        if (Files.exists(serverDatFilePath)) {
            if (!Arrays.equals(currentFileHash, calcServerDatFileHash(Files.readAllBytes(serverDatFilePath)))) {
                throw new DatFileModifiedExternalException(this, serverDatFilePath.toAbsolutePath().toString());
            }
        } else {
            if (!Arrays.equals(currentFileHash, new byte[0])) {
                throw new DatFileModifiedExternalException(this, serverDatFilePath.toAbsolutePath().toString());
            }
        }
    }

    public class ServerStorageEntry {
        private volatile Server server;

        public ServerStorageEntry(Server server) {
            this.server = server;
        }

        public Server getServer() {
            return server;
        }

        public void delete() throws IOException {
            ServerStorage.this.deleteEntry(this);
        }

        public ServerStorage rootStorage() {
            return ServerStorage.this;
        }

        public Path getServerDatFilePath() {
            return serverDatFilePath;
        }
    }

    @FunctionalInterface
    private interface HashCalcer {
        byte[] calc(byte[] input) throws IOException;
    }
}
