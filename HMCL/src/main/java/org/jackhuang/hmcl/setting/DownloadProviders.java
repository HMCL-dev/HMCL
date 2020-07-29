/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.download.AdaptedDownloadProvider;
import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.MojangDownloadProvider;
import org.jackhuang.hmcl.download.OMCMAPIDownloadProvider;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

public final class DownloadProviders {
    private DownloadProviders() {}

    private static final AdaptedDownloadProvider DOWNLOAD_PROVIDER = new AdaptedDownloadProvider();

    public static final Map<String, DownloadProvider> providersById;

    public static final String DEFAULT_PROVIDER_ID = "mcbbs";

    static {
        String bmclapiRoot = "https://bmclapi2.bangbang93.com";
        String bmclapiRootOverride = System.getProperty("hmcl.bmclapi.override");
        if (bmclapiRootOverride != null) bmclapiRoot = bmclapiRootOverride;

        providersById = mapOf(
            pair("mojang", new MojangDownloadProvider()),
            pair("bmclapi", new BMCLAPIDownloadProvider(bmclapiRoot)),
            pair("mcbbs", new BMCLAPIDownloadProvider("https://download.mcbbs.net")),
            pair("mcm", new OMCMAPIDownloadProvider(new HashMap<String, String>() {{
                put("minecraft-meta", "http://mcm.xgheaven.com/minecraft/launcher-meta");
                put("minecraft-launcher", "http://mcm.xgheaven.com/minecraft/launcher");
                put("minecraft-libraries", "http://mcm.xgheaven.com/minecraft/libraries");
                put("minecraft-resources", "http://mcm.xgheaven.com/minecraft/assets");
                put("forge", "http://mcm.xgheaven.com/forge");
                put("fabric-meta", "http://mcm.xgheaven.com/fabric/meta");
                put("fabric-maven", "http://mcm.xgheaven.com/fabric/maven");
            }}))
        );
    }

    static void init() {
        FXUtils.onChangeAndOperate(config().downloadTypeProperty(), downloadType -> {
            DownloadProvider primary = Optional.ofNullable(providersById.get(config().getDownloadType()))
                    .orElse(providersById.get(DEFAULT_PROVIDER_ID));
            DOWNLOAD_PROVIDER.setDownloadProviderCandidates(
                    Stream.concat(
                            Stream.of(primary),
                            providersById.values().stream().filter(x -> x != primary)
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

    public static AdaptedDownloadProvider getDownloadProviderByPrimaryId(String primaryId) {
        AdaptedDownloadProvider adaptedDownloadProvider = new AdaptedDownloadProvider();
        DownloadProvider primary = Optional.ofNullable(providersById.get(primaryId))
                .orElse(providersById.get(DEFAULT_PROVIDER_ID));
        adaptedDownloadProvider.setDownloadProviderCandidates(
                Stream.concat(
                        Stream.of(primary),
                        providersById.values().stream().filter(x -> x != primary)
                ).collect(Collectors.toList())
        );
        return adaptedDownloadProvider;
    }

    /**
     * Get current primary preferred download provider
     */
    public static AdaptedDownloadProvider getDownloadProvider() {
        return DOWNLOAD_PROVIDER;
    }
}
