/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.setting;

import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.UUIDTypeAdapter;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.Lang.tryCast;

final class ConfigUpgrader {
    private ConfigUpgrader() {
    }

    /**
     * This method is for the compatibility with old HMCL 3.x as well as HMCL 2.x.
     *
     * @param deserialized deserialized config settings
     * @param rawJson      raw json structure of the config settings without modification
     */
    static void upgradeConfig(Config deserialized, Map<?, ?> rawJson) {
        upgradeV2(deserialized, rawJson);
        upgradeV3(deserialized, rawJson);
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
                List<OfflineAccount> accounts = new LinkedList<>();
                tryCast(offline.get("uuidMap"), Map.class).ifPresent(uuidMap -> {
                    ((Map<?, ?>) uuidMap).forEach((key, value) -> {
                        OfflineAccount account = OfflineAccountFactory.INSTANCE.create((String) key, UUIDTypeAdapter.fromString((String) value));
                        accounts.add(account);
                        deserialized.getAccountStorages().add(Accounts.getAccountStorage(account));
                    });
                });

                tryCast(offline.get("IAuthenticator_UserName"), String.class).ifPresent(selected -> {
                    if (!rawJson.containsKey("selectedAccount"))
                        accounts.stream().filter(account -> selected.equals(account.getUsername())).findAny().ifPresent(account ->
                                deserialized.setSelectedAccount(Accounts.accountId(account)));
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
    }
}
