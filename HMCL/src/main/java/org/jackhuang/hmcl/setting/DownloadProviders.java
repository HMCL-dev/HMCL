/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.beans.InvalidationListener;
import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.ui.FXUtils;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.task.FetchTask.DEFAULT_CONCURRENCY;

public final class DownloadProviders {
    private DownloadProviders() {
    }

    private static final HMCLDownloadProvider PROVIDER = new HMCLDownloadProvider();

    /// Initializes download provider settings and synchronizes download thread settings.
    public static void init() {
        InvalidationListener onChangeDownloadThreads = observable -> {
            FetchTask.setDownloadExecutorConcurrency(settings().autoDownloadThreadsProperty().get()
                    ? DEFAULT_CONCURRENCY
                    : settings().downloadThreadsProperty().get());
        };
        settings().autoDownloadThreadsProperty().addListener(onChangeDownloadThreads);
        settings().downloadThreadsProperty().addListener(onChangeDownloadThreads);
        onChangeDownloadThreads.invalidated(null);

        FXUtils.onChangeAndOperate(settings().versionListSourceProperty(), PROVIDER::setVersionListSource);
        FXUtils.onChangeAndOperate(settings().fileDownloadSourceProperty(), PROVIDER::setFileSource);
    }

    /**
     * Get current primary preferred download provider
     */
    public static DownloadProvider getDownloadProvider() {
        return PROVIDER;
    }

}
