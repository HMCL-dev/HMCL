// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
