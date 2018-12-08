/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

import java.util.Map;
import java.util.Optional;

import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.MojangDownloadProvider;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.value.ObservableObjectValue;

public final class DownloadProviders {
    private DownloadProviders() {}

    public static final Map<String, DownloadProvider> providersById = mapOf(
            pair("mojang", new MojangDownloadProvider()),
            pair("bmclapi", new BMCLAPIDownloadProvider()));

    public static final String DEFAULT_PROVIDER_ID = "bmclapi";

    private static ObjectBinding<DownloadProvider> downloadProviderProperty;

    static void init() {
        downloadProviderProperty = Bindings.createObjectBinding(
                () -> Optional.ofNullable(providersById.get(config().getDownloadType()))
                        .orElse(providersById.get(DEFAULT_PROVIDER_ID)),
                config().downloadTypeProperty());
    }

    public static DownloadProvider getDownloadProvider() {
        return downloadProviderProperty.get();
    }

    public static ObservableObjectValue<DownloadProvider> downloadProviderProperty() {
        return downloadProviderProperty;
    }
}
