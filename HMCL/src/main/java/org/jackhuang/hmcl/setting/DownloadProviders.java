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
import org.jackhuang.hmcl.util.io.ResponseCodeException;

import javax.net.ssl.SSLHandshakeException;
import java.io.FileNotFoundException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
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

    public static final Map<String, DownloadProvider> providersById;
    public static final Map<String, DownloadProvider> rawProviders;
    private static final AdaptedDownloadProvider fileDownloadProvider = new AdaptedDownloadProvider();

    private static final MojangDownloadProvider MOJANG;
    private static final BMCLAPIDownloadProvider BMCLAPI;

    public static final String DEFAULT_PROVIDER_ID = "balanced";
    public static final String DEFAULT_RAW_PROVIDER_ID = "bmclapi";

    @SuppressWarnings("unused")
    private static final InvalidationListener observer;

    static {
        String bmclapiRoot = "https://bmclapi2.bangbang93.com";
        String bmclapiRootOverride = System.getProperty("hmcl.bmclapi.override");
        if (bmclapiRootOverride != null) bmclapiRoot = bmclapiRootOverride;

        MOJANG = new MojangDownloadProvider();
        BMCLAPI = new BMCLAPIDownloadProvider(bmclapiRoot);
        rawProviders = Map.of(
                "mojang", MOJANG,
                "bmclapi", BMCLAPI
        );

        AdaptedDownloadProvider fileProvider = new AdaptedDownloadProvider();
        fileProvider.setDownloadProviderCandidates(List.of(BMCLAPI, MOJANG));
        BalancedDownloadProvider balanced = new BalancedDownloadProvider(MOJANG, BMCLAPI);

        providersById = Map.of(
                "official", new AutoDownloadProvider(MOJANG, fileProvider),
                "balanced", new AutoDownloadProvider(balanced, fileProvider),
                "mirror", new AutoDownloadProvider(BMCLAPI, fileProvider));

        observer = FXUtils.observeWeak(() -> {
            FetchTask.setDownloadExecutorConcurrency(
                    config().getAutoDownloadThreads() ? DEFAULT_CONCURRENCY : config().getDownloadThreads());
        }, config().autoDownloadThreadsProperty(), config().downloadThreadsProperty());

        provider = new DownloadProviderWrapper(MOJANG);
    }

    static void init() {
        InvalidationListener onChangeDownloadSource = observable -> {
            String versionListSource = Objects.requireNonNullElse(config().getVersionListSource(), "");
            if (config().isAutoChooseDownloadType()) {
                DownloadProvider currentDownloadProvider = providersById.get(versionListSource);
                if (currentDownloadProvider == null)
                    currentDownloadProvider = Objects.requireNonNull(providersById.get(DEFAULT_PROVIDER_ID),
                            "default provider is null");

                provider.setProvider(currentDownloadProvider);
            } else {
                provider.setProvider(fileDownloadProvider);
            }
        };
        config().versionListSourceProperty().addListener(onChangeDownloadSource);
        config().autoChooseDownloadTypeProperty().addListener(onChangeDownloadSource);

        onChangeDownloadSource.invalidated(null);

        FXUtils.onChangeAndOperate(config().downloadTypeProperty(), downloadType -> {
            DownloadProvider primary = Objects.requireNonNullElseGet(
                    rawProviders.get(Objects.requireNonNullElse(downloadType, "")),
                    () -> rawProviders.get(DEFAULT_RAW_PROVIDER_ID));

            List<DownloadProvider> providers = new ArrayList<>(rawProviders.size());
            providers.add(primary);
            for (DownloadProvider provider : rawProviders.values()) {
                if (provider != primary)
                    providers.add(provider);
            }

            fileDownloadProvider.setDownloadProviderCandidates(providers);
        });
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
