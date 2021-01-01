/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import static org.jackhuang.hmcl.util.io.NetworkUtils.toURL;

import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilProvider;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;

import java.net.URL;
import java.util.UUID;

public class AuthlibInjectorProvider implements YggdrasilProvider {

    private final String apiRoot;

    public AuthlibInjectorProvider(String apiRoot) {
        this.apiRoot = apiRoot;
    }

    @Override
    public URL getAuthenticationURL() throws AuthenticationException {
        return toURL(apiRoot + "authserver/authenticate");
    }

    @Override
    public URL getRefreshmentURL() throws AuthenticationException {
        return toURL(apiRoot + "authserver/refresh");
    }

    @Override
    public URL getValidationURL() throws AuthenticationException {
        return toURL(apiRoot + "authserver/validate");
    }

    @Override
    public URL getInvalidationURL() throws AuthenticationException {
        return toURL(apiRoot + "authserver/invalidate");
    }

    @Override
    public URL getSkinUploadURL(UUID uuid) throws UnsupportedOperationException {
        return toURL(apiRoot + "api/user/profile/" + UUIDTypeAdapter.fromUUID(uuid) + "/skin");
    }

    @Override
    public URL getProfilePropertiesURL(UUID uuid) throws AuthenticationException {
        return toURL(apiRoot + "sessionserver/session/minecraft/profile/" + UUIDTypeAdapter.fromUUID(uuid));
    }

    @Override
    public String toString() {
        return apiRoot;
    }
}
