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
package org.jackhuang.hmcl.auth.authlibinjector;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.javafx.ObservableOptionalCache;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public class AuthlibInjectorAccountFactory extends AccountFactory<AuthlibInjectorAccount> {
    private final AuthlibInjectorArtifactProvider downloader;
    private final Function<String, AuthlibInjectorServer> serverLookup;

    /**
     * @param serverLookup a function that looks up {@link AuthlibInjectorServer} by url
     */
    public AuthlibInjectorAccountFactory(AuthlibInjectorArtifactProvider downloader, Function<String, AuthlibInjectorServer> serverLookup) {
        this.downloader = downloader;
        this.serverLookup = serverLookup;
    }

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.USERNAME_PASSWORD;
    }

    @Override
    public AuthlibInjectorAccount create(CharacterSelector selector, String username, String password, ProgressCallback progressCallback, Object additionalData) throws AuthenticationException {
        Objects.requireNonNull(selector);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);

        AuthlibInjectorServer server = (AuthlibInjectorServer) additionalData;

        return new AuthlibInjectorAccount(server, downloader, username, password, selector);
    }

    @Override
    public AuthlibInjectorAccount fromStorage(JsonObject metadata, JsonObject privateData) {
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(privateData);

        String apiRoot = JsonUtils.getString(metadata, "serverBaseURL");
        if (apiRoot == null) {
            throw new IllegalArgumentException("storage does not have API root.");
        }
        AuthlibInjectorServer server = serverLookup.apply(apiRoot);
        return fromStorage(metadata, privateData, downloader, server);
    }

    static AuthlibInjectorAccount fromStorage(
            JsonObject metadata,
            JsonObject privateData,
            AuthlibInjectorArtifactProvider downloader,
            AuthlibInjectorServer server) {
        AccountID accountID = Account.readAccountID(metadata);
        YggdrasilSession session = YggdrasilSession.fromStorage(metadata, privateData);

        String loginName = JsonUtils.getString(metadata, "loginName");
        if (loginName == null) {
            throw new IllegalArgumentException("storage does not have loginName");
        }

        if (privateData.get("profileProperties") instanceof JsonObject profilePropertiesObject) {
            Map<String, String> properties = JsonUtils.GSON.fromJson(
                    profilePropertiesObject,
                    JsonUtils.mapTypeOf(String.class, String.class));
            GameProfile selected = session.selectedProfile();
            ObservableOptionalCache<UUID, CompleteGameProfile, AuthenticationException> profileRepository =
                    server.getYggdrasilService().getProfileRepository();
            profileRepository.put(selected.getId(), new CompleteGameProfile(selected, properties));
            profileRepository.invalidate(selected.getId());
        }

        return new AuthlibInjectorAccount(accountID, server, downloader, loginName, session);
    }
}
