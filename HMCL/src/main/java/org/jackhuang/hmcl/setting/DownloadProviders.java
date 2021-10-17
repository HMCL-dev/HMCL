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

import java.io.FileNotFoundException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.task.FetchTask.DEFAULT_CONCURRENCY;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class DownloadProviders {
    private DownloadProviders() {}

    private static DownloadProvider currentDownloadProvider;

    public static final Map<String, DownloadProvider> providersById;
    public static final Map<String, DownloadProvider> rawProviders;
    private static final AdaptedDownloadProvider fileDownloadProvider = new AdaptedDownloadProvider();

    private static final MojangDownloadProvider MOJANG;
    private static final BMCLAPIDownloadProvider BMCLAPI;
    private static final BMCLAPIDownloadProvider MCBBS;

    public static final String DEFAULT_PROVIDER_ID = "balanced";
    public static final String DEFAULT_RAW_PROVIDER_ID = "mcbbs";

    private static final InvalidationListener observer;

    static {
        String bmclapiRoot = "https://bmclapi2.bangbang93.com";
        String bmclapiRootOverride = System.getProperty("hmcl.bmclapi.override");
        if (bmclapiRootOverride != null) bmclapiRoot = bmclapiRootOverride;

        MOJANG = new MojangDownloadProvider();
        BMCLAPI = new BMCLAPIDownloadProvider(bmclapiRoot);
        MCBBS = new BMCLAPIDownloadProvider("https://download.mcbbs.net");
        rawProviders = mapOf(
                pair("mojang", MOJANG),
                pair("bmclapi", BMCLAPI),
                pair("mcbbs", MCBBS)
        );

        AdaptedDownloadProvider fileProvider = new AdaptedDownloadProvider();
        fileProvider.setDownloadProviderCandidates(Arrays.asList(MCBBS, BMCLAPI, MOJANG));
        BalancedDownloadProvider balanced = new BalancedDownloadProvider(Arrays.asList(MCBBS, BMCLAPI, MOJANG));

        providersById = mapOf(
                pair("official", new AutoDownloadProvider(MOJANG, fileProvider)),
                pair("balanced", new AutoDownloadProvider(balanced, fileProvider)),
                pair("mirror", new AutoDownloadProvider(MCBBS, fileProvider)));

        observer = FXUtils.observeWeak(() -> {
            FetchTask.setDownloadExecutorConcurrency(
                    config().getAutoDownloadThreads() ? DEFAULT_CONCURRENCY : config().getDownloadThreads());
        }, config().autoDownloadThreadsProperty(), config().downloadThreadsProperty());
    }

    static void init() {
        FXUtils.onChangeAndOperate(config().versionListSourceProperty(), versionListSource -> {
            if (!providersById.containsKey(versionListSource)) {
                config().setVersionListSource(DEFAULT_PROVIDER_ID);
                return;
            }

            currentDownloadProvider = Optional.ofNullable(providersById.get(versionListSource))
                    .orElse(providersById.get(DEFAULT_PROVIDER_ID));
        });

        FXUtils.onChangeAndOperate(config().downloadTypeProperty(), downloadType -> {
            if (!rawProviders.containsKey(downloadType)) {
                config().setDownloadType(DEFAULT_RAW_PROVIDER_ID);
                return;
            }

            DownloadProvider primary = Optional.ofNullable(rawProviders.get(downloadType))
                    .orElse(rawProviders.get(DEFAULT_RAW_PROVIDER_ID));
            fileDownloadProvider.setDownloadProviderCandidates(
                    Stream.concat(
                            Stream.of(primary),
                            rawProviders.values().stream().filter(x -> x != primary)
                    ).collect(Collectors.toList())
            );
        });
    }

    public static String getPrimaryDownloadProviderId() {
        String downloadType = config().getDownloadType();
        if (providersById.containsKey(downloadType))
            return downloadType;
        else
            return DEFAULT_PROVIDER_ID;
    }

    public static DownloadProvider getDownloadProviderByPrimaryId(String primaryId) {
        return Optional.ofNullable(providersById.get(primaryId))
                .orElse(providersById.get(DEFAULT_PROVIDER_ID));
    }

    /**
     * Get current primary preferred download provider
     */
    public static DownloadProvider getDownloadProvider() {
        return config().isAutoChooseDownloadType() ? currentDownloadProvider : fileDownloadProvider;
    }

    public static String localizeErrorMessage(Exception exception) {
        if (exception instanceof DownloadException) {
            URL url = ((DownloadException) exception).getUrl();
            if (exception.getCause() instanceof SocketTimeoutException) {
                return i18n("install.failed.downloading.timeout", url);
            } else if (exception.getCause() instanceof ResponseCodeException) {
                ResponseCodeException responseCodeException = (ResponseCodeException) exception.getCause();
                if (I18n.hasKey("download.code." + responseCodeException.getResponseCode())) {
                    return i18n("download.code." + responseCodeException.getResponseCode(), url);
                } else {
                    return i18n("install.failed.downloading.detail", url) + "\n" + StringUtils.getStackTrace(exception.getCause());
                }
            } else if (exception.getCause() instanceof FileNotFoundException) {
                return i18n("download.code.404", url);
            } else if (exception.getCause() instanceof AccessDeniedException) {
                return i18n("install.failed.downloading.detail", url) + "\n" + i18n("exception.access_denied", ((AccessDeniedException) exception.getCause()).getFile());
            } else {
                return i18n("install.failed.downloading.detail", url) + "\n" + StringUtils.getStackTrace(exception.getCause());
            }
        }
        return StringUtils.getStackTrace(exception);
    }
}
