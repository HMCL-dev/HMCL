// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server;

import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ServersDatFileTest {

    @TempDir
    Path tempDir;

    private static Server server(Server.ServerPackStatus status, String icon, String ip, String name) {
        return Server.createServer(name, ip).withServerPackStatus(status).withIcon(icon);
    }

    private static List<CompoundTag> readServers(Path file) throws IOException {
        CompoundTag root = NBTCodec.of().readTag(Files.readAllBytes(file), TagType.COMPOUND);
        Tag serversTag = root.get("servers");
        if (!(serversTag instanceof ListTag<?> servers)) {
            throw new AssertionError("servers tag missing or not ListTag: " + serversTag);
        }
        List<CompoundTag> result = new ArrayList<>();
        for (Tag t : servers) {
            if (!(t instanceof CompoundTag c)) {
                throw new AssertionError("server entry is not CompoundTag: " + t);
            }
            result.add(c);
        }
        return result;
    }

    private static void writeRoot(Path file, CompoundTag root) throws IOException {
        try (OutputStream os = Files.newOutputStream(file)) {
            NBTCodec.of().writeTag(os, root);
        }
    }

    private static void appendGarbage(Path file) throws IOException {
        Files.writeString(file, "modified external", StandardOpenOption.APPEND);
    }

    @Test
    void testReloadFileNotFound() throws Exception {
        ServersDatFile dat = new ServersDatFile(tempDir.resolve("notFound.dat"));

        assertTrue(dat.getEntries().isEmpty());
        assertEquals("", dat.getCurrentFileHash());
    }

    @Test
    void testReloadBadFile() throws Exception {
        Path resolve = tempDir.resolve("badFile.dat");
        Files.writeString(resolve, "bad file.");

        assertThrows(IOException.class, () -> new ServersDatFile(resolve));
    }

    @Test
    void testLoadMissingServersTag() throws IOException {
        Path file = tempDir.resolve("noServersTag.dat");
        writeRoot(file, new CompoundTag());

        assertThrows(IOException.class, () -> new ServersDatFile(file));
    }

    @Test
    void testLoadServersTagNotList() throws IOException {
        Path file = tempDir.resolve("serversNotList.dat");
        CompoundTag root = new CompoundTag();
        root.addString("servers", "oops");
        writeRoot(file, root);

        assertThrows(IOException.class, () -> new ServersDatFile(file));
    }

    @Test
    void testLoadServerEntryNotCompound() throws IOException {
        Path file = tempDir.resolve("entryNotCompound.dat");
        ListTag<StringTag> servers = new ListTag<>();
        servers.addTag(new StringTag("oops"));
        CompoundTag root = new CompoundTag();
        root.addTag("servers", servers);
        writeRoot(file, root);

        assertThrows(IOException.class, () -> new ServersDatFile(file));
    }

    @Test
    void testLoadExistingEntryWithAllFields() throws Exception {
        CompoundTag entry = new CompoundTag();
        entry.addByte("acceptTextures", (byte) 1);
        entry.addByte("hidden", (byte) 1);
        entry.addString("icon", "AAAAA");
        entry.addString("ip", "mc.example.com");
        entry.addString("name", "Example");

        ListTag<CompoundTag> servers = new ListTag<>();
        servers.addTag(entry);
        CompoundTag root = new CompoundTag();
        root.addTag("servers", servers);
        Path file = tempDir.resolve("existing.dat");
        writeRoot(file, root);

        ServersDatFile dat = new ServersDatFile(file);

        assertEquals(1, dat.getEntries().size());
        assertNotEquals("", dat.getCurrentFileHash());

        Server s = dat.getEntries().get(0).getServer();
        assertEquals(Server.ServerPackStatus.ENABLED, s.getServerPackStatus());
        assertEquals("AAAAA", s.getIcon());
        assertEquals("mc.example.com", s.getIp());
        assertEquals("Example", s.getName());
    }

    @Test
    void testLoadEntryWithDefaults() throws Exception {
        CompoundTag entry = new CompoundTag();
        entry.addString("ip", "x");

        ListTag<CompoundTag> servers = new ListTag<>();
        servers.addTag(entry);
        CompoundTag root = new CompoundTag();
        root.addTag("servers", servers);
        Path file = tempDir.resolve("defaults.dat");
        writeRoot(file, root);

        ServersDatFile dat = new ServersDatFile(file);
        Server s = dat.getEntries().get(0).getServer();
        assertEquals(Server.ServerPackStatus.PROMPT, s.getServerPackStatus());
        assertNull(s.getIcon());
        assertEquals("x", s.getIp());
        assertNull(s.getName());
    }

    @Test
    void testNormalOperator() throws Exception {
        Path resolve = tempDir.resolve("normal.dat");
        ServersDatFile dat1 = new ServersDatFile(resolve);

        assertTrue(dat1.getEntries().isEmpty());
        assertEquals("", dat1.getCurrentFileHash());

        Server server = Server.createServer("server.ip", "server name");
        server = server.withServerPackStatus(Server.ServerPackStatus.ENABLED);
        server = server.withIcon("no icon");
        dat1.addServer(server);
        assertEquals(1, dat1.getEntries().size());
        assertNotEquals("", dat1.getCurrentFileHash());

        assertEquals(Server.ServerPackStatus.ENABLED, dat1.getEntries().get(0).getServer().getServerPackStatus());
        dat1.getEntries().get(0).modify(server1 -> server1.withServerPackStatus(Server.ServerPackStatus.DISABLED));
        assertEquals(Server.ServerPackStatus.DISABLED, dat1.getEntries().get(0).getServer().getServerPackStatus());

        dat1.getEntries().get(0).delete();
        assertTrue(dat1.getEntries().isEmpty());
    }

    @Test
    void testAddServerEncoding() throws Exception {
        Path file = tempDir.resolve("encoding.dat");
        ServersDatFile dat = new ServersDatFile(file);

        dat.addServer(server(Server.ServerPackStatus.ENABLED, null, "a", "A"));
        dat.addServer(server(Server.ServerPackStatus.DISABLED, null, "b", "B"));
        dat.addServer(server(Server.ServerPackStatus.PROMPT, "ic", "c", "C"));

        assertEquals(3, dat.getEntries().size());
        List<CompoundTag> entries = readServers(file);
        assertEquals(3, entries.size());

        assertEquals((byte) 1, ((ByteTag) Objects.requireNonNull(entries.get(0).get("acceptTextures"))).getValue());
        assertEquals((byte) 0, ((ByteTag) Objects.requireNonNull(entries.get(1).get("acceptTextures"))).getValue());
        assertNull(entries.get(2).get("acceptTextures"));
        assertEquals("ic", ((StringTag) Objects.requireNonNull(entries.get(2).get("icon"))).getValue());
        assertEquals("c", ((StringTag) Objects.requireNonNull(entries.get(2).get("ip"))).getValue());
        assertEquals("C", ((StringTag) Objects.requireNonNull(entries.get(2).get("name"))).getValue());
    }

    @Test
    void testModifiedExternal() throws Exception {
        Path resolve = tempDir.resolve("add.dat");

        ServersDatFile dat1 = new ServersDatFile(resolve);
        dat1.addServer(server(Server.ServerPackStatus.ENABLED, "no icon", "server.ip", "server name"));
        appendGarbage(resolve);

        assertThrows(ServersDatFile.FileModifiedExternalException.class, () ->
                dat1.addServer(server(Server.ServerPackStatus.ENABLED, "no icon 2", "server.ip.2", "server name 2"))
        );
        assertEquals(1, dat1.getEntries().size());

        assertThrows(ServersDatFile.FileModifiedExternalException.class, () ->
                dat1.getEntries().get(0).delete()
        );
        assertEquals(1, dat1.getEntries().size());

        assertThrows(ServersDatFile.FileModifiedExternalException.class, () ->
                dat1.getEntries().get(0).modify(server -> server.withIp("server.new.ip").withName("server new name"))
        );
        assertEquals(1, dat1.getEntries().size());
    }

    @Test
    void testExternalFileDeleted() throws Exception {
        Path file = tempDir.resolve("deleted.dat");
        ServersDatFile dat = new ServersDatFile(file);
        dat.addServer(server(Server.ServerPackStatus.PROMPT, null, "a", "A"));
        Files.delete(file);

        assertThrows(ServersDatFile.FileModifiedExternalException.class,
                () -> dat.addServer(server(Server.ServerPackStatus.PROMPT, null, "b", "B")));
        assertEquals(1, dat.getEntries().size());
    }

    @Test
    void testModifyRollsBackToOriginalReference() throws Exception {
        Path file = tempDir.resolve("modifyRollback.dat");
        ServersDatFile dat = new ServersDatFile(file);
        dat.addServer(server(Server.ServerPackStatus.PROMPT, null, "a", "A"));

        ServersDatFile.ServerEntry entry = dat.getEntries().get(0);
        Server before = entry.getServer();

        appendGarbage(file);

        assertThrows(ServersDatFile.FileModifiedExternalException.class,
                () -> entry.modify(s -> server(s.getServerPackStatus(), s.getIcon(), s.getIp(), "B")));

        // rollback: original Server instance restored
        assertSame(before, entry.getServer());
    }

    @Test
    void testWrongOperator() throws Exception {
        Path resolve = tempDir.resolve("wrongOperator.dat");

        ServersDatFile dat1 = new ServersDatFile(resolve);
        dat1.addServer(server(Server.ServerPackStatus.ENABLED, "no icon 1", "server.ip 1", "server name 1"));
        dat1.addServer(server(Server.ServerPackStatus.ENABLED, "no icon 2", "server.ip 2", "server name 2"));

        ServersDatFile.ServerEntry last = dat1.getEntries().get(dat1.getEntries().size() - 1);
        last.delete();

        assertEquals(1, dat1.getEntries().size());
        assertThrows(IllegalStateException.class, last::delete);
        assertThrows(IllegalStateException.class,
                () -> last.modify(server -> server.withIp("server.new.ip").withName("server new name")));
    }

    @Test
    void testRoundTripAfterReload() throws Exception {
        Path file = tempDir.resolve("roundTrip.dat");
        ServersDatFile writer = new ServersDatFile(file);
        Server server = Server.createServer("name", "ip");
        server = server.withServerPackStatus(Server.ServerPackStatus.ENABLED);
        server = server.withIcon("i");
        writer.addServer(server);

        ServersDatFile reader = new ServersDatFile(file);
        assertEquals(1, reader.getEntries().size());
        Server s = reader.getEntries().get(0).getServer();
        assertEquals(Server.ServerPackStatus.ENABLED, s.getServerPackStatus());
        assertEquals("i", s.getIcon());
        assertEquals("ip", s.getIp());
        assertEquals("name", s.getName());
    }

    @Test
    void testHashChangesAcrossSaves() throws Exception {
        Path file = tempDir.resolve("hash.dat");
        ServersDatFile dat = new ServersDatFile(file);

        dat.addServer(server(Server.ServerPackStatus.PROMPT, null, "a", "A"));
        String hashAfterAdd = dat.getCurrentFileHash();
        assertNotEquals("", hashAfterAdd);

        dat.addServer(server(Server.ServerPackStatus.PROMPT, null, "b", "B"));
        assertNotEquals(hashAfterAdd, dat.getCurrentFileHash());
    }
}
