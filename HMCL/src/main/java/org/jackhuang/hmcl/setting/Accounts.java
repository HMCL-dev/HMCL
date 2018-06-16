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

import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccountFactory;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorBuildInfo;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServerResponse;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.MojangYggdrasilProvider;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccountFactory;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskResult;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;

/**
 * @author huangyuhui
 */
public final class Accounts {
    private Accounts() {}

    public static final String OFFLINE_ACCOUNT_KEY = "offline";
    public static final String YGGDRASIL_ACCOUNT_KEY = "yggdrasil";
    public static final String AUTHLIB_INJECTOR_ACCOUNT_KEY = "authlibInjector";

    public static final Map<String, AccountFactory<?>> ACCOUNT_FACTORY = mapOf(
            pair(OFFLINE_ACCOUNT_KEY, OfflineAccountFactory.INSTANCE),
            pair(YGGDRASIL_ACCOUNT_KEY, new YggdrasilAccountFactory(MojangYggdrasilProvider.INSTANCE)),
            pair(AUTHLIB_INJECTOR_ACCOUNT_KEY, new AuthlibInjectorAccountFactory(Accounts::downloadAuthlibInjector, Accounts::getOrCreateAuthlibInjectorServer))
    );

    private static final Map<String, String> AUTHLIB_INJECTOR_SERVER_NAMES = new HashMap<>();

    public static String getAccountType(Account account) {
        if (account instanceof OfflineAccount) return OFFLINE_ACCOUNT_KEY;
        else if (account instanceof AuthlibInjectorAccount) return AUTHLIB_INJECTOR_ACCOUNT_KEY;
        else if (account instanceof YggdrasilAccount) return YGGDRASIL_ACCOUNT_KEY;
        else return YGGDRASIL_ACCOUNT_KEY;
    }

    static String getAccountId(Account account) {
        return getAccountId(account.getUsername(), account.getCharacter());
    }

    static String getAccountId(String username, String character) {
        return username + ":" + character;
    }

    private static String downloadAuthlibInjector() throws Exception {
        AuthlibInjectorBuildInfo buildInfo = AuthlibInjectorBuildInfo.requestBuildInfo();
        File jar = new File(Launcher.HMCL_DIRECTORY, "authlib-injector.jar");
        File local = new File(Launcher.HMCL_DIRECTORY, "authlib-injector.txt");
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

    public static String getAuthlibInjectorServerName(String serverIp) throws Exception {
        if (AUTHLIB_INJECTOR_SERVER_NAMES.containsKey(serverIp))
            return AUTHLIB_INJECTOR_SERVER_NAMES.get(serverIp);
        else {
            AuthlibInjectorServerResponse response = Constants.GSON.fromJson(NetworkUtils.doGet(NetworkUtils.toURL(serverIp)), AuthlibInjectorServerResponse.class);
            AUTHLIB_INJECTOR_SERVER_NAMES.put(serverIp, response.getMeta().getServerName());
            return response.getMeta().getServerName();
        }
    }

    @Deprecated
    public static TaskResult<String> getAuthlibInjectorServerNameAsync(AuthlibInjectorAccount account) {
        return Task.ofResult("serverName", () -> Accounts.getAuthlibInjectorServerName(account.getServerBaseURL()));
    }

    private static AuthlibInjectorServer getOrCreateAuthlibInjectorServer(String url) {
        return Settings.SETTINGS.authlibInjectorServers.stream()
                .filter(server -> url.equals(server.getUrl()))
                .findFirst()
                .orElseGet(() -> {
                    // this usually happens when migrating data from an older version
                    String name;
                    try {
                        name = Accounts.getAuthlibInjectorServerName(url);
                        LOG.info("Migrated authlib injector server [" + url + "], name=[" + name + "]");
                    } catch (Exception e) {
                        name = url;
                        LOG.log(Level.WARNING, "Failed to migrate authlib injector server [" + url + "]", e);
                    }
                    AuthlibInjectorServer server = new AuthlibInjectorServer(url, name);
                    Settings.SETTINGS.authlibInjectorServers.add(server);
                    return server;
                });
    }
}
