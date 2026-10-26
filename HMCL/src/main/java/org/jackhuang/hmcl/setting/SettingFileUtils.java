// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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

    /// Moves an invalid config file to a numbered backup path (for example `launcher-settings.json.1`)
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
