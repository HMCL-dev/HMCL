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
import org.jackhuang.hmcl.ui.FXUtils;
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.task.FetchTask.DEFAULT_CONCURRENCY;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class DownloadProviders {
    private DownloadProviders() {
    }

    private static final DownloadProviderWrapper provider;

    public static final Map<String, DownloadProvider> RAW_PROVIDERS;
    private static final MojangDownloadProvider MOJANG;
    private static final BMCLAPIDownloadProvider BMCLAPI;
    private static final DownloadProvider DEFAULT_PROVIDER;

    public static final Map<String, DownloadProvider> providersById;

    @SuppressWarnings("unused")
    private static final InvalidationListener observer;

    static {
        String bmclapiRoot = "https://bmclapi2.bangbang93.com";
        String bmclapiRootOverride = System.getProperty("hmcl.bmclapi.override");
        if (bmclapiRootOverride != null) bmclapiRoot = bmclapiRootOverride;

        MOJANG = new MojangDownloadProvider();
        BMCLAPI = new BMCLAPIDownloadProvider(bmclapiRoot);
        DEFAULT_PROVIDER = BMCLAPI;
        RAW_PROVIDERS = Map.of(
                "mojang", MOJANG,
                "bmclapi", BMCLAPI
        );

        DownloadProvider fileProvider = LocaleUtils.IS_CHINA_MAINLAND
                ? new AdaptedDownloadProvider(BMCLAPI, MOJANG)
                : MOJANG;

        providersById = Map.of(
                "balanced", new AutoDownloadProvider(
                        LocaleUtils.IS_CHINA_MAINLAND ? List.of(MOJANG, BMCLAPI) : List.of(MOJANG),
                        fileProvider),
                "official", new AutoDownloadProvider(List.of(MOJANG), fileProvider),
                "mirror", new AutoDownloadProvider(List.of(BMCLAPI, MOJANG), fileProvider)
        );

        observer = FXUtils.observeWeak(() -> {
            FetchTask.setDownloadExecutorConcurrency(
                    config().getAutoDownloadThreads() ? DEFAULT_CONCURRENCY : config().getDownloadThreads());
        }, config().autoDownloadThreadsProperty(), config().downloadThreadsProperty());

        provider = new DownloadProviderWrapper(MOJANG);
    }

    static void init() {
        InvalidationListener onChangeDownloadSource = observable -> {
            if (config().isAutoChooseDownloadType()) {
                String versionListSource = Objects.requireNonNullElse(config().getVersionListSource(), "");
                DownloadProvider currentDownloadProvider = providersById.getOrDefault(versionListSource, DEFAULT_PROVIDER);
                provider.setProvider(currentDownloadProvider);
            } else {
                String downloadType = config().getDownloadType();
                DownloadProvider primary = RAW_PROVIDERS.getOrDefault(
                        Objects.requireNonNullElse(downloadType, ""),
                        DEFAULT_PROVIDER
                );

                if (primary == MOJANG) {
                    provider.setProvider(MOJANG);
                } else {
                    provider.setProvider(new AdaptedDownloadProvider(primary, MOJANG));
                }
            }
        };
        config().versionListSourceProperty().addListener(onChangeDownloadSource);
        config().autoChooseDownloadTypeProperty().addListener(onChangeDownloadSource);
        config().downloadTypeProperty().addListener(onChangeDownloadSource);

        onChangeDownloadSource.invalidated(null);
    }

    /**
     * Get current primary preferred download provider
     */
    public static DownloadProvider getDownloadProvider() {
        return provider;
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
            } else if (exception.getCause() instanceof SSLHandshakeException) {
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
