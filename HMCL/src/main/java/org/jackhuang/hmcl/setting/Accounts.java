/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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

import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.OfflineAccount;
import org.jackhuang.hmcl.auth.OfflineAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccountFactory;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;

import java.util.Map;

/**
 * @author huangyuhui
 */
public final class Accounts {
    private Accounts() {}

    public static final String OFFLINE_ACCOUNT_KEY = "offline";
    public static final String YGGDRASIL_ACCOUNT_KEY = "yggdrasil";

    public static final Map<String, AccountFactory<?>> ACCOUNT_FACTORY = Lang.mapOf(
            new Pair<>(OFFLINE_ACCOUNT_KEY, OfflineAccountFactory.INSTANCE),
            new Pair<>(YGGDRASIL_ACCOUNT_KEY, YggdrasilAccountFactory.INSTANCE)
    );

    public static String getAccountType(Account account) {
        if (account instanceof OfflineAccount) return OFFLINE_ACCOUNT_KEY;
        else if (account instanceof YggdrasilAccount) return YGGDRASIL_ACCOUNT_KEY;
        else return YGGDRASIL_ACCOUNT_KEY;
    }

    public static void setCurrentCharacter(Account account, String character) {
        account.getProperties().put("character", character);
    }

    public static String getCurrentCharacter(Account account) {
        return Lang.get(account.getProperties(), "character", String.class, null);
    }

    public static String getCurrentCharacter(Map<Object, Object> storage) {
        Map properties = Lang.get(storage, "properties", Map.class, null);
        if (properties == null) return null;
        return Lang.get(properties, "character", String.class, null);
    }

    static String getAccountId(Account account) {
        return getAccountId(account.getUsername(), getCurrentCharacter(account));
    }

    static String getAccountId(String username, String character) {
        return username + ":" + character;
    }
}
