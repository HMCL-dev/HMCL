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
package org.jackhuang.hmcl.auth.yggdrasil;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.UUID;

import org.jackhuang.hmcl.util.Immutable;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;

/**
 * @author yushijinhun
 */
@Immutable
public class CompleteGameProfile extends GameProfile {

    @JsonAdapter(PropertyMapSerializer.class)
    private final Map<String, String> properties;

    public CompleteGameProfile(UUID id, String name, Map<String, String> properties) {
        super(id, name);
        this.properties = requireNonNull(properties);
    }

    public CompleteGameProfile(GameProfile profile, Map<String, String> properties) {
        this(profile.getId(), profile.getName(), properties);
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public void validate() throws JsonParseException {
        super.validate();

        if (properties == null)
            throw new JsonParseException("Game profile properties cannot be null");
    }
}
