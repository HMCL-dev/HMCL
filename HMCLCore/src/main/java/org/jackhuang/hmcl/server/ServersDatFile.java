// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server;

import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.CompoundTag;
import org.glavo.nbt.tag.ListTag;
import org.glavo.nbt.tag.Tag;
import org.glavo.nbt.tag.TagType;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

public class ServersDatFile {
    private static final String MD5 = "MD5";
    private final Lock handleLock = new ReentrantLock();
    private final @NotNull Path file;

    private @NotNull List<@NotNull ServerEntry> entries = new ArrayList<>(0);
    private @NotNull String currentFileHash = "";
    private @NotNull CompoundTag currentRootTag = new CompoundTag();

    public ServersDatFile(@NotNull Path file) throws IOException {
        this.file = file;
        reload();
    }

    public @NotNull @Unmodifiable List<@NotNull ServerEntry> getEntries() {
        return List.copyOf(entries);
    }

    public @NotNull Path getFile() {
        return file;
    }

    @VisibleForTesting
    public @NotNull String getCurrentFileHash() {
        return currentFileHash;
    }

    public void reload() throws IOException {
        handleLock.lock();
        try {
            CompoundTag currentRootTag;
            String currentFileHash;
            List<ServerEntry> entries;
            if (Files.exists(file)) {
                byte[] bytes = Files.readAllBytes(file);
                currentFileHash = DigestUtils.digestToString(MD5, bytes);

                entries = new ArrayList<>();

                currentRootTag = NBTCodec.of().readTag(bytes, TagType.COMPOUND);
                Tag serversTag = currentRootTag.get("servers");
                if (serversTag == null) {
                    throw new IOException("servers tag not found");
                } else if (!(serversTag instanceof ListTag<?>)) {
                    throw new IOException("servers tag is not a ListTag");
                }

                for (Tag serverEntryTag : ((ListTag<?>) serversTag)) {
                    if (serverEntryTag instanceof CompoundTag serverEntryTagCompound) {
                        entries.add(new ServerEntry(new Server(serverEntryTagCompound.clone())));
                    } else {
                        throw new IOException("server entry tag is not a CompoundTag");
                    }
                }
            } else {
                currentFileHash = "";
                entries = new ArrayList<>(0);
                currentRootTag = new CompoundTag();
            }
            this.currentRootTag = currentRootTag;
            this.currentFileHash = currentFileHash;
            this.entries = entries;
        } finally {
            handleLock.unlock();
        }
    }

    private void save() throws IOException {
        handleLock.lock();
        try {
            // check md5
            if (Files.exists(file)) {
                if (!currentFileHash.equals(DigestUtils.digestToString(MD5, file))) {
                    throw new FileModifiedExternalException();
                }
            } else {
                if (!currentFileHash.isEmpty()) {
                    throw new FileModifiedExternalException();
                }
            }

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                ListTag<CompoundTag> serversTag = new ListTag<>();
                for (ServerEntry entry : entries) {
                    serversTag.addTag(entry.server.serverTag.clone());
                }

                currentRootTag.addTag("servers", serversTag);
                NBTCodec.of().writeTag(outputStream, currentRootTag);

                byte[] bytes = outputStream.toByteArray();

                FileUtils.saveSafely(file, op ->
                        op.write(bytes)
                );

                currentFileHash = DigestUtils.digestToString(MD5, bytes);
            }
        } finally {
            handleLock.unlock();
        }
    }

    public ServerEntry addServer(@NotNull Server server) throws IOException {
        handleLock.lock();
        try {
            ServerEntry entry = new ServerEntry(Objects.requireNonNull(server));
            try {
                entries.add(entry);
                this.save();
                return entry;
            } catch (Exception e) {
                entries.remove(entry);
                throw e;
            }

        } finally {
            handleLock.unlock();
        }
    }

    public static class FileModifiedExternalException extends IOException {

    }

    public final class ServerEntry {
        private volatile @NotNull Server server;

        private ServerEntry(@NotNull Server server) {
            this.server = server;
        }

        public @NotNull Server getServer() {
            return server;
        }

        public void delete() throws IOException {
            ServersDatFile.this.handleLock.lock();
            try {
                int index = ServersDatFile.this.entries.indexOf(this);
                if (index == -1) {
                    throw new IllegalStateException("server entry has been deleted.");
                }

                ServersDatFile.this.entries.remove(index);
                try {
                    ServersDatFile.this.save();
                } catch (Exception t) {
                    ServersDatFile.this.entries.add(index, this);
                    throw t;
                }
            } finally {
                ServersDatFile.this.handleLock.unlock();
            }
        }

        public void modify(@NotNull UnaryOperator<@NotNull Server> uo) throws IOException {
            ServersDatFile.this.handleLock.lock();
            try {
                if (ServersDatFile.this.entries.contains(this)) {
                    Server oldServer = server;
                    this.server = Objects.requireNonNull(uo.apply(this.server));
                    try {
                        ServersDatFile.this.save();
                    } catch (Exception t) {
                        this.server = oldServer;
                        throw t;
                    }
                } else {
                    throw new IllegalStateException("server entry does not exist.");
                }
            } finally {
                ServersDatFile.this.handleLock.unlock();
            }
        }

        public ServersDatFile getServersDatFile() {
            return ServersDatFile.this;
        }
    }
}
