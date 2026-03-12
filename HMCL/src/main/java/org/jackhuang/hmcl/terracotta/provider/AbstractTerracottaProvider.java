/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
