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
package org.jackhuang.hmcl.addon.mod;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Utility class for checking the integrity of mod (ZIP) files.
///
/// Uses [java.util.zip.ZipFile] to detect damage in mod archives. Two levels of checking
/// are available; see [check] for their exact semantics.
@NotNullByDefault
public final class ModIntegrityChecker {

    /// Buffer size for reading ZIP entry data.
    private static final int BUFFER_SIZE = 64 * 1024;

    /// Maximum number of bytes to decompress across all entries in strict mode.
    ///
    /// Strict mode must decompress entries to verify their checksums, so a highly compressible
    /// archive (a decompression bomb) would otherwise consume unbounded CPU and time. Genuine
    /// mods stay far below this bound; exceeding it yields [Result#UNKNOWN] because the check
    /// could not be completed, not because the archive is known to be damaged.
    private static final long MAX_TOTAL_DECOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024;

    /// Maximum number of entries to inspect.
    ///
    /// Bounds the loop itself: an archive made of millions of tiny entries would stay under the
    /// byte limit while still consuming unbounded CPU. Exceeding it yields [Result#UNKNOWN].
    private static final int MAX_ENTRIES = 100_000;

    private ModIntegrityChecker() {
    }

    /// The outcome of an integrity check.
    public enum Result {
        /// The archive is structurally sound (and, in strict mode, all verifiable checksums match).
        INTACT,

        /// The archive is damaged.
        CORRUPT,

        /// The check could not be performed, e.g. the file is missing or locked by another
        /// process, or the archive exceeds the inspection limits. This is distinct from
        /// [INTACT]: the mod's state is unknown, so the result must not be cached as known-good.
        UNKNOWN
    }

    /// Checks the integrity of the given mod file.
    ///
    /// Non-strict mode only verifies that the archive can be opened and its entry table
    /// enumerated. This catches the structural failures visible in the central directory — a
    /// missing end-of-central-directory record or a malformed central directory header —
    /// without decompressing any entry data, so it is fast and immune to decompression bombs.
    /// Damage confined to an entry's local header or payload is not visible here; strict mode
    /// is required to detect it.
    ///
    /// Strict mode additionally decompresses entries up to the inspection limits
    /// ([MAX_ENTRIES] and [MAX_TOTAL_DECOMPRESSED_BYTES]) and verifies their CRC32 checksum
    /// and uncompressed size. This detects damaged entry payloads that a structurally valid
    /// archive can still contain, but such mods often still launch, so the result is advisory.
    /// Entries that record no usable CRC32 in the central directory are skipped rather than
    /// failing the whole archive, so a single such entry cannot make an otherwise-verifiable
    /// mod permanently unverifiable. In practice [java.util.zip.ZipFile] always resolves the
    /// checksum from the central directory, so this path is defensive.
    ///
    /// @param file the mod file to check
    /// @param strict whether to perform strict CRC32 verification
    /// @return [Result#CORRUPT] when damage was found; [Result#UNKNOWN] when the file could not
    ///         be read, exceeds the inspection limits, or contained no verifiable entry;
    ///         [Result#INTACT] otherwise
    public static Result check(Path file, boolean strict) {
        try (ZipFile zipFile = new ZipFile(file.toFile())) {
            if (!strict) {
                // Opening the archive and enumerating its entries validates the central
                // directory without reading any entry payload. The enumeration itself is the
                // validation, so its result is intentionally discarded.
                zipFile.entries();
                return Result.INTACT;
            }

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            byte[] buffer = new byte[BUFFER_SIZE];
            CRC32 crc = new CRC32();
            long totalDecompressed = 0;
            int entryCount = 0;
            int verifiedEntries = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++entryCount > MAX_ENTRIES) {
                    LOG.warning("Mod archive has too many entries to verify, skipping: " + file);
                    return Result.UNKNOWN;
                }
                // Skip directories - they have no data to validate
                if (entry.isDirectory()) {
                    continue;
                }
                crc.reset();
                long actualSize = 0;
                try (InputStream is = zipFile.getInputStream(entry)) {
                    int n;
                    while ((n = is.read(buffer)) != -1) {
                        crc.update(buffer, 0, n);
                        actualSize += n;
                        totalDecompressed += n;
                        if (totalDecompressed > MAX_TOTAL_DECOMPRESSED_BYTES) {
                            // The archive expands far beyond any plausible mod; stop rather than
                            // keep consuming CPU. The file is not known to be damaged.
                            LOG.warning("Mod archive expands beyond the verification limit, skipping: " + file);
                            return Result.UNKNOWN;
                        }
                    }
                }
                // An entry that records no usable CRC32 in the central directory cannot be
                // verified. Skip it rather than failing the whole archive: a single such
                // entry would otherwise make an otherwise-verifiable mod permanently
                // unverifiable. In practice ZipFile always resolves the checksum from the
                // central directory, so this path is defensive.
                if (entry.getCrc() < 0) {
                    LOG.warning("Entry has no CRC32 checksum, cannot verify: " + file
                            + " entry " + sanitizeForLog(entry.getName()));
                    continue;
                }
                verifiedEntries++;
                if (entry.getCrc() != crc.getValue()) {
                    LOG.warning("CRC mismatch in " + file + " entry " + sanitizeForLog(entry.getName()));
                    return Result.CORRUPT;
                }
                // Verify size if available
                if (entry.getSize() >= 0 && entry.getSize() != actualSize) {
                    LOG.warning("Size mismatch in " + file + " entry " + sanitizeForLog(entry.getName()));
                    return Result.CORRUPT;
                }
            }
            // If nothing could be verified, the archive's integrity is genuinely unknown
            return verifiedEntries > 0 ? Result.INTACT : Result.UNKNOWN;
        } catch (ZipException e) {
            // ZIP file is corrupt, log single-line message
            LOG.warning("Mod file is corrupt (invalid ZIP): " + file);
            return Result.CORRUPT;
        } catch (FileSystemException e) {
            // The file is missing, locked by another process, or otherwise inaccessible - the
            // check could not be performed, so the result is unknown rather than "sound".
            // NoSuchFileException and AccessDeniedException are subclasses of this type.
            LOG.warning("Failed to read mod file (environmental error): " + file);
            return Result.UNKNOWN;
        } catch (IOException e) {
            // The file is locked by another process, inaccessible, or hit a transient I/O
            // error. The check could not be performed, so the result is unknown rather
            // than "sound" — and must not be reported as damage.
            LOG.warning("Failed to read mod file, integrity unknown: " + file, e);
            return Result.UNKNOWN;
        }
    }

    /// Makes an archive-controlled string safe to embed in a single log line.
    ///
    /// Entry names come from the archive and may contain line breaks or other control
    /// characters, which would otherwise let a crafted archive forge additional log entries.
    ///
    /// @param name the entry name
    /// @return the name with control characters replaced by `?`
    private static String sanitizeForLog(String name) {
        StringBuilder builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            builder.append(Character.isISOControl(c) ? '?' : c);
        }
        return builder.toString();
    }
}
