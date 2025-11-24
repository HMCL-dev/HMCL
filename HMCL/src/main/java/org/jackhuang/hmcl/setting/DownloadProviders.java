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
import org.jackhuang.hmcl.util.Lang;
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
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.task.FetchTask.DEFAULT_CONCURRENCY;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class DownloadProviders {
    private DownloadProviders() {
    }

    public static final String DEFAULT_AUTO_PROVIDER_ID = "balanced";
    public static final String DEFAULT_DIRECT_PROVIDER_ID = "mojang";

    private static final DownloadProviderWrapper PROVIDER_WRAPPER;
    public static final Map<String, DownloadProvider> DIRECT_PROVIDERS;
    public static final Map<String, DownloadProvider> AUTO_PROVIDERS;

    private static final MojangDownloadProvider RAW_MOJANG;
    private static final BMCLAPIDownloadProvider RAW_BMCLAPI;

    private static final DownloadProvider AUTO_OFFICIAL_PROVIDER;
    private static final DownloadProvider AUTO_MIRROR_PROVIDER;
    private static final DownloadProvider AUTO_BALANCED_PROVIDER;

    static {
        String bmclapiRoot = "https://bmclapi2.bangbang93.com";
        String bmclapiRootOverride = System.getProperty("hmcl.bmclapi.override");
        if (bmclapiRootOverride != null) bmclapiRoot = bmclapiRootOverride;

        RAW_MOJANG = new MojangDownloadProvider();
        RAW_BMCLAPI = new BMCLAPIDownloadProvider(bmclapiRoot);
        DIRECT_PROVIDERS = Lang.mapOf(
                pair("mojang", RAW_MOJANG),
                pair("bmclapi", new AdaptedDownloadProvider(RAW_BMCLAPI, RAW_MOJANG))
        );

        DownloadProvider autoFileProvider = new AdaptedDownloadProvider(RAW_BMCLAPI, RAW_MOJANG);
        if (LocaleUtils.IS_CHINA_MAINLAND) {
            AUTO_OFFICIAL_PROVIDER = new AutoDownloadProvider(List.of(RAW_MOJANG, RAW_BMCLAPI), autoFileProvider);
            AUTO_BALANCED_PROVIDER = new AutoDownloadProvider(List.of(RAW_MOJANG, RAW_BMCLAPI), autoFileProvider);
        } else {
            AUTO_OFFICIAL_PROVIDER = RAW_MOJANG;
            AUTO_BALANCED_PROVIDER = RAW_MOJANG;
        }
        AUTO_MIRROR_PROVIDER = new AutoDownloadProvider(List.of(RAW_BMCLAPI, RAW_MOJANG), autoFileProvider);

        AUTO_PROVIDERS = Lang.mapOf(
                pair("balanced", AUTO_BALANCED_PROVIDER),
                pair("official", AUTO_OFFICIAL_PROVIDER),
                pair("mirror", AUTO_MIRROR_PROVIDER)
        );

        PROVIDER_WRAPPER = new DownloadProviderWrapper(RAW_MOJANG);
    }

    static void init() {
        InvalidationListener onChangeDownloadThreads = observable -> {
            FetchTask.setDownloadExecutorConcurrency(config().getAutoDownloadThreads()
                    ? DEFAULT_CONCURRENCY
                    : config().getDownloadThreads());
        };
        config().autoDownloadThreadsProperty().addListener(onChangeDownloadThreads);
        config().downloadThreadsProperty().addListener(onChangeDownloadThreads);
        onChangeDownloadThreads.invalidated(null);

        InvalidationListener onChangeDownloadSource = observable -> {
            if (config().isAutoChooseDownloadType()) {
                String versionListSource = config().getVersionListSource();
                DownloadProvider downloadProvider = versionListSource != null
                        ? AUTO_PROVIDERS.getOrDefault(versionListSource, RAW_MOJANG)
                        : RAW_MOJANG;
                PROVIDER_WRAPPER.setProvider(downloadProvider);
            } else {
                String downloadType = config().getDownloadType();
                PROVIDER_WRAPPER.setProvider(downloadType != null
                        ? DIRECT_PROVIDERS.getOrDefault(downloadType, RAW_MOJANG)
                        : RAW_MOJANG);
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
