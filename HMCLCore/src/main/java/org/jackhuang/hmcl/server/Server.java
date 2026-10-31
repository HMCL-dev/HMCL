// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server;

import org.glavo.nbt.tag.ByteTag;
import org.glavo.nbt.tag.CompoundTag;
import org.glavo.nbt.tag.StringTag;
import org.glavo.nbt.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// @see <a href="https://minecraft.wiki/w/Servers.dat_format">Servers.dat format</a>
public final class Server {
    final @NotNull CompoundTag serverTag;

    Server(@NotNull CompoundTag serverTag) {
        this.serverTag = serverTag;
    }

    public static Server createServer(@NotNull String name, @NotNull String ip) {
        return createServer(ServerPackStatus.PROMPT, name, ip);
    }

    public static Server createServer(@NotNull ServerPackStatus packStatus, @NotNull String name, @NotNull String ip) {
        CompoundTag tag = new CompoundTag();
        tag.setString("name", name);
        tag.setString("ip", ip);
        return new Server(tag).withServerPackStatus(packStatus);
    }

    private @Nullable String getString(@NotNull String name) {
        if (serverTag.get(name) instanceof StringTag tag) {
            return tag.getValue();
        }
        return null;
    }

    private @Nullable Byte getByte(@NotNull String name) {
        if (serverTag.get(name) instanceof ByteTag tag) {
            return tag.getValue();
        }
        return null;
    }

    private Server withString(@NotNull String name, @Nullable String value) {
        if (!Objects.equals(getString(name), value)) {
            CompoundTag cloned = serverTag.clone();

            if (value == null) {
                Tag tag = cloned.get(name);
                if (tag != null) {
                    cloned.removeTag(tag);
                }
            } else {
                cloned.setString(name, value);
            }

            return new Server(cloned);
        }
        return this;
    }

    private Server withByte(@NotNull String name, @Nullable Byte value) {
        if (!Objects.equals(getByte(name), value)) {
            CompoundTag cloned = serverTag.clone();

            if (value == null) {
                Tag tag = cloned.get(name);
                if (tag != null) {
                    cloned.removeTag(tag);
                }
            } else {
                cloned.setByte(name, value);
            }

            return new Server(cloned);
        }
        return this;
    }

    public @Nullable String getName() {
        return getString("name");
    }

    public boolean isHidden() {
        Byte b = getByte("hidden");
        if (b == null) return false;
        return b == 1;
    }

    public @Nullable String getIp() {
        return getString("ip");
    }

    public @Nullable String getIcon() {
        return getString("icon");
    }

    public Server withName(@Nullable String name) {
        return withString("name", name);
    }

    public Server withIp(@Nullable String ip) {
        return withString("ip", ip);
    }

    public Server withIcon(@Nullable String icon) {
        return withString("icon", icon);
    }

    public @NotNull ServerPackStatus getServerPackStatus() {
        Byte acceptTextures = getByte("acceptTextures");

        if (acceptTextures == null) {
            return ServerPackStatus.PROMPT;
        }
        if (acceptTextures == 0) {
            return ServerPackStatus.DISABLED;
        }
        return ServerPackStatus.ENABLED;
    }

    public Server withServerPackStatus(@NotNull ServerPackStatus serverPackStatus) {
        return withByte("acceptTextures", serverPackStatus.data);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Server server = (Server) o;
        return Objects.equals(serverTag, server.serverTag);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(serverTag);
    }

    @Override
    public String toString() {
        return "Server{" +
                "serverTag=" + serverTag +
                '}';
    }

    public enum ServerPackStatus {
        ENABLED((byte) 1),
        DISABLED((byte) 0),
        PROMPT(null);

        private final @Nullable Byte data;

        ServerPackStatus(@Nullable Byte data) {
            this.data = data;
        }
    }
}
