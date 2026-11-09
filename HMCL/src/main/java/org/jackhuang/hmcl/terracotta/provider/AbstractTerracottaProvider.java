// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.terracotta.provider;

import javafx.beans.value.ObservableDoubleValue;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.TerracottaBundle;
import org.jackhuang.hmcl.util.FXThread;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;

public abstract class AbstractTerracottaProvider {
    public enum Status {
        NOT_EXIST,
        LEGACY_VERSION,
        READY
    }

    public interface DownloadContext {
        @FXThread
        void bindProgress(ObservableDoubleValue progress);

        void checkCancellation() throws CancellationException;
    }

    protected final TerracottaBundle bundle;

    protected AbstractTerracottaProvider(TerracottaBundle bundle) {
        this.bundle = bundle;
    }

    public Status status() throws IOException {
        return bundle.status();
    }

    public final Task<Path> download(DownloadContext progress) {
        return bundle.download(progress);
    }

    public Task<?> install(Path pkg) throws IOException {
        return bundle.install(pkg);
    }

    public abstract List<String> ofCommandLine(Path portTransfer);
}
