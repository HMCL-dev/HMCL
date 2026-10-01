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
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Utility class for checking the integrity of mod (ZIP) files.
///
/// Uses [java.util.zip.ZipFile] to perform deep validation by reading all entries
/// and verifying their CRC32 checksums. This detects corruption that
/// [org.jackhuang.hmcl.util.io.CompressingUtils] may miss, since the latter
/// only validates the central directory structure.
@NotNullByDefault
public final class ModIntegrityChecker {

    private ModIntegrityChecker() {
    }

    /// Checks whether the given mod file is corrupt.
    ///
    /// A file is considered corrupt if:
    /// - It cannot be opened as a ZIP file at all.
    /// - Any entry's CRC32 checksum does not match its data.
    /// - Any entry cannot be read due to data corruption.
    ///
    /// @param file the mod file to check
    /// @return `true` if the file is corrupt, `false` if it is intact
    public static boolean isCorrupt(Path file) {
        try (ZipFile zipFile = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                // Skip directories - they have no data to validate
                if (entry.isDirectory()) continue;
                try (InputStream is = zipFile.getInputStream(entry)) {
                    // Read fully to trigger CRC32 validation
                    while (is.read(buffer) != -1) {
                        // Just consume the stream
                    }
                }
            }
            return false;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            LOG.warning("Mod file is corrupt: " + file, e);
            return true;
        }
    }
}
