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
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.task.FetchTask.DEFAULT_CONCURRENCY;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class DownloadProviders {
    private DownloadProviders() {
    }

    private static final String DEFAULT_BMCLAPI_ROOT = "https://bmclapi2.bangbang93.com";
    private static final String BMCLAPI_ROOT;

    static {
        @Nullable String bmclOverride = System.getProperty("hmcl.bmclapi.override");
        if (StringUtils.isBlank(bmclOverride)) {
            BMCLAPI_ROOT = DEFAULT_BMCLAPI_ROOT;
        } else {
            WebURL url;
            try {
                url = WebURL.parseBrowserInput(bmclOverride);
            } catch (Exception e) {
                url = null;
                LOG.warning("Invalid BMCLAPI override URL: " + bmclOverride, e);
            }

            if (url == null) {
                BMCLAPI_ROOT = DEFAULT_BMCLAPI_ROOT;
            } else {
                BMCLAPI_ROOT = StringUtils.removeSuffix(url.toString(), "/");
            }
        }
    }

    private static final HMCLDownloadProvider PROVIDER = new HMCLDownloadProvider(BMCLAPI_ROOT);

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
