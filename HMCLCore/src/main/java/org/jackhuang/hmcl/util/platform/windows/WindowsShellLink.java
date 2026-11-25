// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_16LE;

/// Reads stored local targets from files in the
/// [Shell Link Binary File Format](https://learn.microsoft.com/openspecs/windows_protocols/ms-shllink/).
@NotNullByDefault
public final class WindowsShellLink {

    /// Size in bytes of a ShellLinkHeader.
    private static final int HEADER_SIZE = 0x4C;

    /// The Shell Link CLSID as serialized in the header.
    private static final byte @Unmodifiable [] LINK_CLSID = HexFormat.of().parseHex("0114020000000000c000000000000046");

    /// Maximum shortcut size read during a launcher startup check.
    private static final int MAX_FILE_SIZE = 1024 * 1024;

    /// Reads the drive-qualified local target recorded in the shortcut's LinkInfo.
    /// Unicode path fields take precedence over their counterparts in the [SystemInfo#NATIVE_CHARSET].
    /// The returned Windows path can be inspected on any host, even if the target no longer exists.
    ///
    /// @param shortcut the link file to read
    /// @return the stored path, or `null` for unsupported targets, invalid path data or links larger than 1 MiB
    /// @throws IOException if the link file cannot be read
    public static @Nullable String readLocalTarget(Path shortcut) throws IOException {
        byte[] bytes;
        try (InputStream in = Files.newInputStream(shortcut)) {
            bytes = in.readNBytes(MAX_FILE_SIZE + 1);
        }

        ByteBuffer file = ByteBuffer.wrap(bytes).order(LITTLE_ENDIAN);
        if (bytes.length < HEADER_SIZE || bytes.length > MAX_FILE_SIZE || file.getInt(0) != HEADER_SIZE
                || !Arrays.equals(bytes, 4, 20, LINK_CLSID, 0, LINK_CLSID.length))
            return null;

        int flags = file.getInt(20);
        // ForceNoLinkInfo, HasExpString, HasDarwinID and PreferEnvironmentPath can override the stored path.
        int unsupported = 0x100 | 0x200 | 0x1000 | 0x02000000;
        if ((flags & 2) == 0 || (flags & unsupported) != 0)
            return null;

        int offset = HEADER_SIZE;
        if ((flags & 1) != 0) {
            if (bytes.length - offset < 2)
                return null;
            int idListSize = Short.toUnsignedInt(file.getShort(offset));
            offset += 2 + idListSize;
            if (idListSize < 2 || offset > bytes.length || file.getShort(offset - 2) != 0)
                return null;
        }

        if (bytes.length - offset < 4)
            return null;
        int size = file.getInt(offset);
        if (size < 0x1C || size > bytes.length - offset || !hasValidTail(file, offset + size, flags))
            return null;

        return readLinkInfo(file.slice(offset, size).order(LITTLE_ENDIAN));
    }

    /// Checks the lengths of the optional StringData and ExtraData records following LinkInfo.
    private static boolean hasValidTail(ByteBuffer file, int offset, int flags) {
        int charSize = (flags & 0x80) == 0 ? 1 : 2;
        for (int flag = 0x04; flag <= 0x40; flag <<= 1) {
            if ((flags & flag) != 0) {
                if (file.limit() - offset < 2)
                    return false;
                int length = Short.toUnsignedInt(file.getShort(offset)) * charSize;
                if (length > file.limit() - offset - 2)
                    return false;
                offset += 2 + length;
            }
        }

        while (file.limit() - offset >= 4) {
            int size = file.getInt(offset);
            if (size >= 0 && size < 4)
                return file.limit() - offset == 4;
            if (size < 8 || size > file.limit() - offset)
                return false;
            offset += size;
        }
        return false;
    }

    /// Reads the local base path and common suffix from a bounded LinkInfo record.
    private static @Nullable String readLinkInfo(ByteBuffer info) {
        int headerSize = info.getInt(4);
        if ((headerSize != 0x1C && headerSize < 0x24) || headerSize > info.limit())
            return null;
        // Only VolumeIDAndLocalBasePath is supported. A network mapping may have an alternate UNC target.
        if (info.getInt(8) != 1 || info.getInt(20) != 0)
            return null;
        int volumeOffset = info.getInt(12);
        if (volumeOffset < headerSize || volumeOffset > info.limit() - 4)
            return null;
        int volumeSize = info.getInt(volumeOffset);
        if (volumeSize <= 16 || volumeSize > info.limit() - volumeOffset)
            return null;

        // Validate all declared field offsets, including the ANSI copies in Unicode links.
        int lastSlot = headerSize >= 0x24 ? 32 : 24;
        for (int slot = 16; slot <= lastSlot; slot += 4) {
            int offset = info.getInt(slot);
            if (slot == 20 || (slot >= 28 && offset == 0))
                continue;
            if (offset < headerSize || offset >= info.limit()
                    || (offset >= volumeOffset && offset < volumeOffset + volumeSize))
                return null;
        }

        @Nullable String base = readPathField(info, lastSlot, 16, 28);
        @Nullable String suffix = readPathField(info, lastSlot, 24, 32);
        if (base == null || suffix == null || base.length() < 3 || base.charAt(1) != ':' || base.charAt(2) != '\\')
            return null;
        char drive = base.charAt(0);
        if (!(drive >= 'A' && drive <= 'Z' || drive >= 'a' && drive <= 'z'))
            return null;
        if (suffix.isEmpty())
            return base;
        if (base.endsWith("\\"))
            return base + (suffix.startsWith("\\") ? suffix.substring(1) : suffix);
        return base + (suffix.startsWith("\\") ? "" : "\\") + suffix;
    }

    /// Decodes a null-terminated LinkInfo path field, preferring its optional UTF-16LE representation.
    private static @Nullable String readPathField(ByteBuffer info, int lastSlot, int ansiSlot, int unicodeSlot) {
        int unicodeOffset = unicodeSlot <= lastSlot ? info.getInt(unicodeSlot) : 0;
        int offset = unicodeOffset != 0 ? unicodeOffset : info.getInt(ansiSlot);
        int width = unicodeOffset != 0 ? 2 : 1;
        Charset charset = unicodeOffset != 0 ? UTF_16LE : SystemInfo.NATIVE_CHARSET;

        // A missing terminator must not consume bytes from the next field.
        int limit = info.limit();
        for (int slot = 12; slot <= lastSlot; slot += 4) {
            int next = info.getInt(slot);
            if (next > offset && next < limit)
                limit = next;
        }
        for (int end = offset; end <= limit - width; end += width) {
            if (info.get(end) == 0 && (width == 1 || info.get(end + 1) == 0)) {
                try {
                    return charset.newDecoder().decode(info.slice(offset, end - offset)).toString();
                } catch (CharacterCodingException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /// Prevents instantiation.
    private WindowsShellLink() {
    }
}
