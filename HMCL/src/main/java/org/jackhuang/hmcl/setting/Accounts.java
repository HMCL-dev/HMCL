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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.OfflineAccount;
import org.jackhuang.hmcl.auth.OfflineAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.*;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.util.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author huangyuhui
 */
public final class Accounts {
    private Accounts() {}

    public static final String OFFLINE_ACCOUNT_KEY = "offline";
    public static final String YGGDRASIL_ACCOUNT_KEY = "yggdrasil";
    public static final String AUTHLIB_INJECTOR_ACCOUNT_KEY = "authlibInjector";

    public static final Map<String, AccountFactory<?>> ACCOUNT_FACTORY = Lang.mapOf(
            new Pair<>(OFFLINE_ACCOUNT_KEY, OfflineAccountFactory.INSTANCE),
            new Pair<>(YGGDRASIL_ACCOUNT_KEY, new YggdrasilAccountFactory()),
            new Pair<>(AUTHLIB_INJECTOR_ACCOUNT_KEY, new AuthlibInjectorAccountFactory(Accounts::downloadAuthlibInjector))
    );

    private static final Map<String, String> AUTHLIB_INJECTOR_SERVER_NAMES = new HashMap<>();

    public static String getAccountType(Account account) {
        if (account instanceof OfflineAccount) return OFFLINE_ACCOUNT_KEY;
        else if (account instanceof AuthlibInjectorAccount) return AUTHLIB_INJECTOR_ACCOUNT_KEY;
        else if (account instanceof YggdrasilAccount) return YGGDRASIL_ACCOUNT_KEY;
        else return YGGDRASIL_ACCOUNT_KEY;
    }

    public static void setCurrentCharacter(Account account, String character) {
        account.getProperties().put("character", character);
    }

    public static boolean hasCurrentCharacter(Account account) {
        return Lang.get(account.getProperties(), "character", String.class, null) != null;
    }

    public static String getCurrentCharacter(Account account) {
        return Lang.get(account.getProperties(), "character", String.class)
                .orElseThrow(() -> new IllegalArgumentException("Account " + account + " has not set current character."));
    }

    public static Optional<String> getCurrentCharacter(Map<Object, Object> storage) {
        Optional<Map> properties = Lang.get(storage, "properties", Map.class);
        if (!properties.isPresent()) return Optional.empty();
        return Lang.get(properties.get(), "character", String.class);
    }

    static String getAccountId(Account account) {
        return getAccountId(account.getUsername(), getCurrentCharacter(account));
    }

    static String getAccountId(String username, String character) {
        return username + ":" + character;
    }

    private static String downloadAuthlibInjector() throws Exception {
        AuthlibInjectorBuildInfo buildInfo = AuthlibInjectorBuildInfo.requestBuildInfo();
        File jar = new File(Main.HMCL_DIRECTORY, "authlib-injector.jar");
        File local = new File(Main.HMCL_DIRECTORY, "authlib-injector.txt");
        int buildNumber = 0;
        try {
            buildNumber = Integer.parseInt(FileUtils.readText(local));
        } catch (IOException | NumberFormatException ignore) {
        }
        if (buildNumber < buildInfo.getBuildNumber()) {
            new FileDownloadTask(new URL(buildInfo.getUrl()), jar).run();
            FileUtils.writeText(local, String.valueOf(buildInfo.getBuildNumber()));
        }
        return jar.getAbsolutePath();
    }

    public static String getAuthlibInjectorServerName(String serverIp) {
        if (AUTHLIB_INJECTOR_SERVER_NAMES.containsKey(serverIp))
            return AUTHLIB_INJECTOR_SERVER_NAMES.get(serverIp);
        else {
            try {
                AuthlibInjectorServerResponse response = Constants.GSON.fromJson(NetworkUtils.doGet(NetworkUtils.toURL(serverIp)), AuthlibInjectorServerResponse.class);
                AUTHLIB_INJECTOR_SERVER_NAMES.put(serverIp, response.getMeta().getServerName());
                return response.getMeta().getServerName();
            } catch (JsonParseException | IOException | NullPointerException e) {
                return null;
            }
        }
    }
}
