// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.upgrade;

import org.jackhuang.hmcl.task.FileDownloadTask;

import java.nio.file.Path;

public final class HMCLDownloadTask extends FileDownloadTask {
    public HMCLDownloadTask(RemoteVersion version, Path target) {
        super(version.url(), target, version.integrityCheck());
    }
}
