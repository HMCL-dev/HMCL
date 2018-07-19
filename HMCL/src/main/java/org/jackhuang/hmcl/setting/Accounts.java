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
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDownloader;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.MojangYggdrasilProvider;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccountFactory;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;

import static org.jackhuang.hmcl.setting.ConfigHolder.CONFIG;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;

/**
 * @author huangyuhui
 */
public final class Accounts {
    private Accounts() {}

    public static final OfflineAccountFactory FACTORY_OFFLINE = OfflineAccountFactory.INSTANCE;
    public static final YggdrasilAccountFactory FACTORY_YGGDRASIL = new YggdrasilAccountFactory(MojangYggdrasilProvider.INSTANCE);
    public static final AuthlibInjectorAccountFactory FACTORY_AUTHLIB_INJECTOR = new AuthlibInjectorAccountFactory(
            new AuthlibInjectorDownloader(Launcher.HMCL_DIRECTORY.toPath(), () -> Settings.INSTANCE.getDownloadProvider())::getArtifactInfo,
            Accounts::getOrCreateAuthlibInjectorServer);

    public static final String OFFLINE_ACCOUNT_KEY = "offline";
    public static final String YGGDRASIL_ACCOUNT_KEY = "yggdrasil";
    public static final String AUTHLIB_INJECTOR_ACCOUNT_KEY = "authlibInjector";

    public static final Map<String, AccountFactory<?>> ACCOUNT_FACTORY = mapOf(
            pair(OFFLINE_ACCOUNT_KEY, FACTORY_OFFLINE),
            pair(YGGDRASIL_ACCOUNT_KEY, FACTORY_YGGDRASIL),
            pair(AUTHLIB_INJECTOR_ACCOUNT_KEY, FACTORY_AUTHLIB_INJECTOR)
    );

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

    private static AuthlibInjectorServer getOrCreateAuthlibInjectorServer(String url) {
        return CONFIG.getAuthlibInjectorServers().stream()
                .filter(server -> url.equals(server.getUrl()))
                .findFirst()
                .orElseGet(() -> {
                    // this usually happens when migrating data from an older version
                    AuthlibInjectorServer server;
                    try {
                        server = AuthlibInjectorServer.fetchServerInfo(url);
                        LOG.info("Migrated authlib injector server " + server);
                    } catch (IOException e) {
                        server = new AuthlibInjectorServer(url, url);
                        LOG.log(Level.WARNING, "Failed to migrate authlib injector server " + url, e);
                    }

                    CONFIG.getAuthlibInjectorServers().add(server);
                    return server;
                });
    }
}
