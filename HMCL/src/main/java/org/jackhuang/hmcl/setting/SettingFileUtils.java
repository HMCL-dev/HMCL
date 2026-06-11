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
package org.jackhuang.hmcl.setting;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Utility methods shared by launcher settings files.
@NotNullByDefault
public final class SettingFileUtils {
    /// Prevents instantiation.
    private SettingFileUtils() {
    }

    /// Moves an invalid config file to a numbered backup path (for example `settings.json.1`)
    /// so the original bytes are preserved for diagnosis before the next successful overwrite.
    public static void backupInvalidConfig(Path location) {
        try {
            Path backup = null;
            for (int i = 1; i < Integer.MAX_VALUE; i++) {
                Path candidate = location.resolveSibling(location.getFileName() + "." + i);
                if (!Files.exists(candidate)) {
                    backup = candidate;
                    break;
                }
            }
            if (backup == null) {
                LOG.warning("Could not find an available backup path for " + location);
                return;
            }
            Files.move(location, backup, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Backed up invalid config to " + backup);
        } catch (IOException e) {
            LOG.warning("Failed to back up invalid config " + location, e);
        }
    }
}
