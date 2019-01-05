/*
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
package org.jackhuang.hmcl.auth.yggdrasil;

import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.net.URL;
import java.util.UUID;

public class MojangYggdrasilProvider implements YggdrasilProvider {
    public static final MojangYggdrasilProvider INSTANCE = new MojangYggdrasilProvider();

    @Override
    public URL getAuthenticationURL() {
        return NetworkUtils.toURL("https://authserver.mojang.com/authenticate");
    }

    @Override
    public URL getRefreshmentURL() {
        return NetworkUtils.toURL("https://authserver.mojang.com/refresh");
    }

    @Override
    public URL getValidationURL() {
        return NetworkUtils.toURL("https://authserver.mojang.com/validate");
    }

    @Override
    public URL getInvalidationURL() {
        return NetworkUtils.toURL("https://authserver.mojang.com/invalidate");
    }

    @Override
    public URL getProfilePropertiesURL(UUID uuid) {
        return NetworkUtils.toURL("https://sessionserver.mojang.com/session/minecraft/profile/" + UUIDTypeAdapter.fromUUID(uuid));
    }
}
