// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.upgrade;

import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

final class HMCLDownloadTask extends FileDownloadTask {

    private final RemoteVersion.Type archiveFormat;

    public HMCLDownloadTask(RemoteVersion version, Path target) {
        super(version.url(), target, version.integrityCheck());
        archiveFormat = version.type();
    }

    /// Downloads the update and validates its archive format.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        super.execute(context);

        try {
            Path target = getPath();
            switch (archiveFormat) {
                case JAR:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown format: " + archiveFormat);
            }
        } catch (Throwable e) {
            try {
                Files.deleteIfExists(getPath());
            } catch (Throwable e2) {
                e.addSuppressed(e2);
            }
            throw e;
        }
        return null;
    }

}
