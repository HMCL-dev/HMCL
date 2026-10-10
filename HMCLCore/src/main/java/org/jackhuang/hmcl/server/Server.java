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
package org.jackhuang.hmcl.server;

import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.*;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Server {
    private final @NotNull ServerPackStatus serverPackStatus;
    private final boolean hidden;
    private final @Nullable String icon;
    private final @Nullable String ip;
    private final @Nullable String name;

    public Server(@NotNull ServerPackStatus serverPackStatus, boolean hidden, @Nullable String icon, @Nullable String ip, @Nullable String name) {
        this.serverPackStatus = serverPackStatus;
        this.hidden = hidden;
        this.icon = icon;
        this.ip = ip;
        this.name = name;
    }

    public static Server loadSingle(@NotNull CompoundTag tag) {
        return new Server(
                tag.get("acceptTextures") instanceof ByteTag bt ? (bt.getValue() == 0 ? ServerPackStatus.DISABLED : ServerPackStatus.ENABLED) : ServerPackStatus.PROMPT,
                tag.get("hidden") instanceof ByteTag bt && bt.getValue() != 0,
                tag.get("icon") instanceof StringTag stg ? stg.getValue() : null,
                tag.get("ip") instanceof StringTag stg ? stg.getValue() : null,
                tag.get("name") instanceof StringTag stg ? stg.getValue() : null
        );
    }

    public static List<Server> loadFromServersDat(@NotNull Path file) throws IOException {
        List<Server> servers = new ArrayList<>();

        CompoundTag root = NBTCodec.of().readTag(file, TagType.COMPOUND);
        Tag serversTag = root.get("servers");
        if (serversTag == null) {
            throw new IOException("servers tag not found");
        } else if (!(serversTag instanceof ListTag<?>)) {
            throw new IOException("servers tag is not a ListTag");
        }

        for (Tag serverEntryTag : ((ListTag<?>) serversTag)) {
            if (serverEntryTag instanceof CompoundTag serverEntryTagCompound) {
                servers.add(loadSingle(serverEntryTagCompound));
            } else {
                throw new IOException("server entry tag is not a CompoundTag");
            }
        }

        return servers;
    }

    public static <T extends Server> void saveToServersDat(@NotNull List<T> servers, @NotNull Path file) throws IOException {
        ListTag<CompoundTag> tag = new ListTag<>();
        for (Server server : servers) {
            CompoundTag serverTag = new CompoundTag();
            server.writeToCompoundTag(serverTag);
            tag.addTag(serverTag);
        }

        CompoundTag root = new CompoundTag();
        root.addTag("servers", tag);

        FileUtils.saveSafely(file, outputStream ->
                NBTCodec.of().writeTag(outputStream, root)
        );
    }

    public void writeToCompoundTag(CompoundTag tag) {
        switch (serverPackStatus) {
            case ENABLED -> tag.addByte("acceptTextures", (byte) 1);
            case DISABLED -> tag.addByte("acceptTextures", (byte) 0);
            case PROMPT -> {
            }
        }
        tag.addByte("hidden", (byte) (hidden ? 1 : 0));
        if (icon != null) tag.addString("icon", icon);
        if (ip != null) tag.addString("ip", ip);
        if (name != null) tag.addString("name", name);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Server server = (Server) o;
        return serverPackStatus == server.serverPackStatus && hidden == server.hidden && Objects.equals(icon, server.icon) && Objects.equals(ip, server.ip) && Objects.equals(name, server.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverPackStatus, hidden, icon, ip, name);
    }

    public @NotNull ServerPackStatus getServerPackStatus() {
        return serverPackStatus;
    }

    public boolean isHidden() {
        return hidden;
    }

    public @Nullable String getIcon() {
        return icon;
    }

    public @Nullable String getIp() {
        return ip;
    }

    public @Nullable String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Server{" +
                "acceptTextures=" + serverPackStatus +
                ", hidden=" + hidden +
                ", icon='" + icon + '\'' +
                ", ip='" + ip + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

    public enum ServerPackStatus {
        ENABLED(),
        DISABLED(),
        PROMPT();
    }
}
