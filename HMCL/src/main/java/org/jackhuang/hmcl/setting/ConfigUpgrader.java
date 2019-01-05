/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.util.StringUtils;
import java.util.HashMap;
import java.util.Map;

import static org.jackhuang.hmcl.util.Lang.tryCast;

final class ConfigUpgrader {
    private static final int VERSION = 0;

    private ConfigUpgrader() {
    }

    /**
     * This method is for the compatibility with old HMCL 3.x as well as HMCL 2.x.
     *
     * @param deserialized deserialized config settings
     * @param rawJson      raw json structure of the config settings without modification
     * @return true if config version is upgraded
     */
    static boolean upgradeConfig(Config deserialized, Map<?, ?> rawJson) {
        boolean upgraded;
        if (deserialized.getConfigVersion() < VERSION) {
            deserialized.setConfigVersion(VERSION);
            // TODO: Add upgrade code here.
            upgraded = true;
        } else {
            upgraded = false;
        }

        upgradeV2(deserialized, rawJson);
        upgradeV3(deserialized, rawJson);

        return upgraded;
    }

    /**
     * Upgrade configuration of HMCL 2.x
     *
     * @param deserialized deserialized config settings
     * @param rawJson      raw json structure of the config settings without modification
     */
    private static void upgradeV2(Config deserialized, Map<?, ?> rawJson) {
        // Convert OfflineAccounts whose stored uuid is important.
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
    }

    /**
     * Upgrade configuration of HMCL earlier than 3.1.70
     *
     * @param deserialized deserialized config settings
     * @param rawJson      raw json structure of the config settings without modification
     */
    private static void upgradeV3(Config deserialized, Map<?, ?> rawJson) {
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

        tryCast(rawJson.get("selectedAccount"), String.class)
                .ifPresent(selected -> {
                    deserialized.getAccountStorages().stream()
                            .filter(storage -> {
                                Object type = storage.get("type");
                                if ("offline".equals(type)) {
                                    return selected.equals(storage.get("username") + ":" + storage.get("username"));
                                } else if ("yggdrasil".equals(type) || "authlibInjector".equals(type)) {
                                    return selected.equals(storage.get("username") + ":" + storage.get("displayName"));
                                } else {
                                    return false;
                                }
                            })
                            .findFirst()
                            .ifPresent(storage -> storage.put("selected", true));
                });
    }
}
