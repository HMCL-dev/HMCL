// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;

import static java.nio.charset.StandardCharsets.UTF_16LE;
import static org.junit.jupiter.api.Assertions.*;

/// Verifies local path extraction from Shell links independently of the host filesystem.
@NotNullByDefault
final class WindowsShellLinkTest {

    /// Position following the 76-byte header and the length-prefixed Computer item ID list.
    private static final int LINK_INFO_OFFSET = 100;

    /// Holds the link file read by each test.
    @TempDir
    Path directory;

    /// The IsUnicode flag applies to StringData even when the local target uses the native code page.
    @Test
    void readsNativePathAfterIdList() throws IOException {
        String folder = OperatingSystem.NATIVE_CHARSET.newEncoder().canEncode("\u6e38\u620f") ? "\u6e38\u620f" : "Games";
        String target = "C:\\" + folder + "\\HMCL.exe";
        assertEquals(target, read(shortcut(target, "", null, null)));
    }

    /// Both Unicode path fields take precedence over their ANSI counterparts.
    @Test
    void prefersUnicodeBaseAndSuffix() throws IOException {
        assertEquals("D:\\\u6e38\u620f\\\u542f\u52a8.exe",
                read(shortcut("C:\\Games\\", "old.exe", "D:\\\u6e38\u620f\\", "\u542f\u52a8.exe")));
    }

    /// Path metadata that the Shell ignores cannot authorize removal of a shortcut.
    @Test
    void ignoresOverriddenLocalPath() throws IOException {
        byte[] link = shortcut("C:\\old.exe", "", null, null);
        ByteBuffer header = ByteBuffer.wrap(link).order(ByteOrder.LITTLE_ENDIAN);
        for (int flags : new int[]{
                0x81,       // No HasLinkInfo
                0x183,      // ForceNoLinkInfo
                0x02000283  // HasExpString and PreferEnvironmentPath
        }) {
            header.putInt(20, flags);
            assertNull(read(link), Integer.toHexString(flags));
        }
    }

    /// Reads LinkInfo immediately after the header when no target ID list is stored.
    @Test
    void readsLocalLinkWithoutIdList() throws IOException {
        byte[] link = shortcut("C:\\Games", "launcher.exe", null, null);
        byte[] withoutIdList = Arrays.copyOf(link, link.length - (LINK_INFO_OFFSET - 76));
        System.arraycopy(link, LINK_INFO_OFFSET, withoutIdList, 76, link.length - LINK_INFO_OFFSET);
        ByteBuffer.wrap(withoutIdList).order(ByteOrder.LITTLE_ENDIAN).putInt(20, 2);
        assertEquals("C:\\Games\\launcher.exe", read(withoutIdList));
    }

    /// Out-of-block offsets and truncated records must not produce a partial target path.
    @Test
    void rejectsInvalidBounds() throws IOException {
        byte[] link = shortcut("C:\\old.exe", "", null, null);
        assertNull(read(Arrays.copyOf(link, 19)));
        assertNull(read(Arrays.copyOf(link, LINK_INFO_OFFSET - 1)));
        assertNull(read(Arrays.copyOf(link, link.length - 5)));
        assertNull(read(Arrays.copyOf(link, link.length - 1)));
        ByteBuffer info = ByteBuffer.wrap(link).order(ByteOrder.LITTLE_ENDIAN);
        info.putInt(LINK_INFO_OFFSET + 16, -1);
        assertNull(read(link));
        info.putInt(LINK_INFO_OFFSET + 16, 4);
        assertNull(read(link));
        info.putInt(LINK_INFO_OFFSET + 16, info.getInt(LINK_INFO_OFFSET + 12));
        assertNull(read(link));
    }

    /// An invalid UTF-16 sequence must not be replaced by the ANSI fallback or a guessed character.
    @Test
    void rejectsMalformedUnicode() throws IOException {
        byte[] link = shortcut("C:\\fallback.exe", "", "D:\\x", "");
        ByteBuffer data = ByteBuffer.wrap(link).order(ByteOrder.LITTLE_ENDIAN);
        int wideBaseOffset = data.getInt(LINK_INFO_OFFSET + 28);
        data.putChar(LINK_INFO_OFFSET + wideBaseOffset + 6, Character.MIN_HIGH_SURROGATE);
        assertNull(read(link));
    }

    /// Skips a complete Unicode description but rejects one extending past the end of the file.
    @Test
    void validatesTrailingStringData() throws IOException {
        byte[] plain = shortcut("C:\\launcher.exe", "", null, null);
        byte[] description = "Launcher".getBytes(UTF_16LE);
        byte[] link = Arrays.copyOf(plain, plain.length + 2 + description.length);
        ByteBuffer data = ByteBuffer.wrap(link).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(20, 0x87); // HasName in addition to the fixture's normal flags.
        data.position(plain.length - 4).putShort((short) (description.length / 2)).put(description).putInt(0);
        assertEquals("C:\\launcher.exe", read(link));
        data.putShort(plain.length - 4, (short) 0xFFFF);
        assertNull(read(link));
    }

    /// Writes the fixture bytes to a temporary file and reads its stored target.
    private @Nullable String read(byte[] link) throws IOException {
        return WindowsShellLink.readLocalTarget(Files.write(directory.resolve("launcher.lnk"), link));
    }

    /// Builds a local Shell link with independently encoded ANSI and optional Unicode path fields.
    private static byte[] shortcut(String base, String suffix, @Nullable String wideBase, @Nullable String wideSuffix) {
        ByteBuffer link = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
        link.putInt(76).put(HexFormat.of().parseHex("0114020000000000c000000000000046"));
        link.putInt(0x83); // HasLinkTargetIDList, HasLinkInfo, IsUnicode
        link.position(60).putInt(1); // SW_SHOWNORMAL
        link.position(76).putShort((short) 22);
        link.put(HexFormat.of().parseHex("14001f50e04fd020ea3a6910a2d808002b30309d0000"));

        int start = link.position();
        int headerSize = wideBase == null ? 0x1C : 0x24;
        link.putInt(start + 4, headerSize);
        link.putInt(start + 8, 1); // VolumeIDAndLocalBasePath
        link.putInt(start + 12, headerSize);
        link.position(start + headerSize);
        link.putInt(17).putInt(3).putInt(0).putInt(16).put((byte) 0); // Fixed drive, empty volume label.
        link.putInt(start + 16, link.position() - start);
        link.put(base.getBytes(OperatingSystem.NATIVE_CHARSET)).put((byte) 0);
        link.putInt(start + 24, link.position() - start);
        link.put(suffix.getBytes(OperatingSystem.NATIVE_CHARSET)).put((byte) 0);
        if (wideBase != null && wideSuffix != null) {
            link.putInt(start + 28, link.position() - start);
            link.put(wideBase.getBytes(UTF_16LE)).putShort((short) 0);
            link.putInt(start + 32, link.position() - start);
            link.put(wideSuffix.getBytes(UTF_16LE)).putShort((short) 0);
        }
        link.putInt(start, link.position() - start);
        link.putInt(0); // TerminalBlock
        return Arrays.copyOf(link.array(), link.position());
    }
}
