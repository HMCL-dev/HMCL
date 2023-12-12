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

import com.google.gson.Gson;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.Logging.LOG;

final class ConfigUpgrader {
    private ConfigUpgrader() {
    }

    private static final int CURRENT_VERSION = 2;

    /**
     * This method is for the compatibility with old HMCL versions.
     *
     * @param deserialized deserialized config settings
     * @param rawContent   raw json content of the config settings without modification
     */
    static void upgradeConfig(Config deserialized, String rawContent) {
        if (deserialized.getConfigVersion() == CURRENT_VERSION) {
            return;
        }

        int configVersion = deserialized.getConfigVersion();
        if (configVersion > CURRENT_VERSION) {
            LOG.log(Level.WARNING, String.format("Current HMCL only support the configuration version up to %d. However, the version now is %d.", CURRENT_VERSION, configVersion));
            return;
        }

        LOG.log(Level.INFO, String.format("Updating configuration from %d to %d.", configVersion, CURRENT_VERSION));
        Map<?, ?> unmodifiableRawJson = Collections.unmodifiableMap(new Gson().<Map<?, ?>>fromJson(rawContent, Map.class));
        for (Map.Entry<Integer, BiConsumer<Config, Map<?, ?>>> dfu : collectDFU()) {
            if (configVersion < dfu.getKey()) {
                dfu.getValue().accept(deserialized, unmodifiableRawJson);
                configVersion = dfu.getKey();
            }
        }

        deserialized.setConfigVersion(CURRENT_VERSION);
    }

    /**
     * <p>Initialize the dfu of HMCL. Feel free to use lambda as all the lambda here would not be initialized unless HMCL needs to update the configuration.
     * For each item in this list, it should be a Map.Entry.</p>
     *
     * <p>The key should be a version number. All the configuration with a version number which is less than the specific one will be applied to this upgrader.</p>
     * <p>The value should be the upgrader. The configuration which is waited to being processed, and the raw unmodifiable value of the json from the configuration file.</p>
     * <p>The return value should a target version number of this item.</p>
     *
     * <p>The last item must return CURRENT_VERSION, as the config file should always being updated to the latest version.</p>
     */
    private static List<Map.Entry<Integer, BiConsumer<Config, Map<?, ?>>>> collectDFU() {
        List<Map.Entry<Integer, BiConsumer<Config, Map<?, ?>>>> dfu = Lang.immutableListOf(
                Pair.pair(1, (deserialized, rawJson) -> {
                    // Upgrade configuration of HMCL 2.x: Convert OfflineAccounts whose stored uuid is important.
                    tryCast(rawJson.get("auth"), Map.class).ifPresent(auth -> {
                        tryCast(auth.get("offline"), Map.class).ifPresent(offline -> {
                            String selected = rawJson.containsKey("selectedAccount") ? null
                                    : tryCast(offline.get("IAuthenticator_UserName"), String.class).orElse(null);

                            tryCast(offline.get("uuidMap"), Map.class).ifPresent(uuidMap -> {
                                ((Map<?, ?>) uuidMap).forEach((key, value) -> {
                                    Map<Object, Object> storage = new HashMap<>();
                                    storage.put("type", "offline");
                                    storage.put("username", key);
                                    storage.put("uuid", value);
                                    if (key.equals(selected)) {
                                        storage.put("selected", true);
                                    }
                                    deserialized.getAccountStorages().add(storage);
                                });
                            });
                        });
                    });

                    // Upgrade configuration of HMCL earlier than 3.1.70
                    if (!rawJson.containsKey("commonDirType"))
                        deserialized.setCommonDirType(deserialized.getCommonDirectory().equals(Settings.getDefaultCommonDirectory()) ? EnumCommonDirectory.DEFAULT : EnumCommonDirectory.CUSTOM);
                    if (!rawJson.containsKey("backgroundType"))
                        deserialized.setBackgroundImageType(StringUtils.isNotBlank(deserialized.getBackgroundImage()) ? EnumBackgroundImage.CUSTOM : EnumBackgroundImage.DEFAULT);
                    if (!rawJson.containsKey("hasProxy"))
                        deserialized.setHasProxy(StringUtils.isNotBlank(deserialized.getProxyHost()));
                    if (!rawJson.containsKey("hasProxyAuth"))
                        deserialized.setHasProxyAuth(StringUtils.isNotBlank(deserialized.getProxyUser()));

                    if (!rawJson.containsKey("downloadType")) {
                        tryCast(rawJson.get("downloadtype"), Number.class)
                                .map(Number::intValue)
                                .ifPresent(id -> {
                                    if (id == 0) {
                                        deserialized.setDownloadType("mojang");
                                    } else if (id == 1) {
                                        deserialized.setDownloadType("bmclapi");
                                    }
                                });
                    }
                }),
                Pair.pair(2, (deserialized, rawJson) -> {
                    deserialized.setX(0.5D - deserialized.getWidth() / Controllers.SCREEN.getBounds().getWidth() / 2);
                    deserialized.setY(0.5D - deserialized.getHeight() / Controllers.SCREEN.getBounds().getHeight() / 2);
                })
        );

        if (dfu.get(dfu.size() - 1).getKey() != CURRENT_VERSION) {
            throw new IllegalStateException("The last dfu must adapt all the config version below CURRENT_VERSION");
        }

        return dfu;
    }
}
