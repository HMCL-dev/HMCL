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
package org.jackhuang.hmcl.auth.yggdrasil;

import org.jackhuang.hmcl.util.Immutable;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Immutable
public final class TextureResponse {
    private final UUID profileId;
    private final String profileName;
    private final Map<TextureType, Texture> textures;

    public TextureResponse() {
        this(UUID.randomUUID(), "", Collections.emptyMap());
    }

    public TextureResponse(UUID profileId, String profileName, Map<TextureType, Texture> textures) {
        this.profileId = profileId;
        this.profileName = profileName;
        this.textures = textures;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public Map<TextureType, Texture> getTextures() {
        return textures == null ? null : Collections.unmodifiableMap(textures);
    }
}
