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
import org.jackhuang.hmcl.task.DownloadException;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;

import javax.net.ssl.SSLHandshakeException;
import java.io.FileNotFoundException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.task.FetchTask.DEFAULT_CONCURRENCY;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class DownloadProviders {
    private DownloadProviders() {
    }

    private static final DownloadProviderWrapper PROVIDER_WRAPPER;

    private static final DownloadProvider MOJANG_PROVIDER;
    private static final BMCLAPIDownloadProvider BMCLAPI_PROVIDER;
    private static final DownloadProvider DEFAULT_PROVIDER;

    static {
        String bmclapiRoot = System.getProperty("hmcl.bmclapi.override", "https://bmclapi2.bangbang93.com");
        BMCLAPI_PROVIDER = new BMCLAPIDownloadProvider(bmclapiRoot);
        MOJANG_PROVIDER = new MojangDownloadProvider();
        DEFAULT_PROVIDER = createDownloadProvider(DownloadSource.DEFAULT, DownloadSource.DEFAULT);
        PROVIDER_WRAPPER = new DownloadProviderWrapper(DEFAULT_PROVIDER);
    }

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

        InvalidationListener onChangeDownloadSource = observable -> {
            PROVIDER_WRAPPER.setProvider(createDownloadProvider(
                    settings().versionListSourceProperty().get(),
                    settings().fileDownloadSourceProperty().get()));
        };
        settings().versionListSourceProperty().addListener(onChangeDownloadSource);
        settings().fileDownloadSourceProperty().addListener(onChangeDownloadSource);
        onChangeDownloadSource.invalidated(null);
    }

    /// Creates a download provider with independent version-list and file download preferences.
    private static DownloadProvider createDownloadProvider(DownloadSource versionListSource, DownloadSource fileDownloadSource) {
        return new AutoDownloadProvider(
                getCandidates(versionListSource),
                getCandidates(fileDownloadSource));
    }

    /// Returns provider candidates ordered by the given source preference.
    private static List<DownloadProvider> getCandidates(DownloadSource source) {
        DownloadSource normalized = source != null ? source : DownloadSource.DEFAULT;
        return switch (normalized) {
            case DEFAULT -> LocaleUtils.IS_CHINA_MAINLAND
                    ? List.of(BMCLAPI_PROVIDER, MOJANG_PROVIDER)
                    : List.of(MOJANG_PROVIDER, BMCLAPI_PROVIDER);
            case OFFICIAL -> List.of(MOJANG_PROVIDER, BMCLAPI_PROVIDER);
            case MIRROR -> List.of(BMCLAPI_PROVIDER, MOJANG_PROVIDER);
        };
    }

    /**
     * Get current primary preferred download provider
     */
    public static DownloadProvider getDownloadProvider() {
        return PROVIDER_WRAPPER;
    }

    public static String localizeErrorMessage(Throwable exception) {
        if (exception instanceof DownloadException) {
            URI uri = ((DownloadException) exception).getUri();
            if (exception.getCause() instanceof SocketTimeoutException) {
                return i18n("install.failed.downloading.timeout", uri);
            } else if (exception.getCause() instanceof ResponseCodeException) {
                ResponseCodeException responseCodeException = (ResponseCodeException) exception.getCause();
                if (I18n.hasKey("download.code." + responseCodeException.getResponseCode())) {
                    return i18n("download.code." + responseCodeException.getResponseCode(), uri);
                } else {
                    return i18n("install.failed.downloading.detail", uri) + "\n" + StringUtils.getStackTrace(exception.getCause());
                }
            } else if (exception.getCause() instanceof FileNotFoundException) {
                return i18n("download.code.404", uri);
            } else if (exception.getCause() instanceof AccessDeniedException) {
                return i18n("install.failed.downloading.detail", uri) + "\n" + i18n("exception.access_denied", ((AccessDeniedException) exception.getCause()).getFile());
            } else if (exception.getCause() instanceof ArtifactMalformedException) {
                return i18n("install.failed.downloading.detail", uri) + "\n" + i18n("exception.artifact_malformed");
            } else if (exception.getCause() instanceof SSLHandshakeException && !(exception.getCause().getMessage() != null && exception.getCause().getMessage().contains("Remote host terminated"))) {
                if (exception.getCause().getMessage() != null && (exception.getCause().getMessage().contains("No name matching") || exception.getCause().getMessage().contains("No subject alternative DNS name matching"))) {
                    return i18n("install.failed.downloading.detail", uri) + "\n" + i18n("exception.dns.pollution");
                }
                return i18n("install.failed.downloading.detail", uri) + "\n" + i18n("exception.ssl_handshake");
            } else {
                return i18n("install.failed.downloading.detail", uri) + "\n" + StringUtils.getStackTrace(exception.getCause());
            }
        } else if (exception instanceof ArtifactMalformedException) {
            return i18n("exception.artifact_malformed");
        } else if (exception instanceof CancellationException) {
            return i18n("message.cancelled");
        }
        return StringUtils.getStackTrace(exception);
    }
}
