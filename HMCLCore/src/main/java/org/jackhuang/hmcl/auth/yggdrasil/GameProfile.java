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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.Validation;

import java.util.UUID;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class GameProfile implements Validation {

    private final UUID id;
    private final String name;
    private final PropertyMap properties;

    public GameProfile() {
        this(null, null);
    }

    public GameProfile(UUID id, String name) {
        this(id, name, new PropertyMap());
    }

    public GameProfile(UUID id, String name, PropertyMap properties) {
        this.id = id;
        this.name = name;
        this.properties = properties;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * @return nullable
     */
    public PropertyMap getProperties() {
        return properties;
    }

    @Override
    public void validate() throws JsonParseException {
        if (id == null)
            throw new JsonParseException("Game profile id cannot be null or malformed");
        if (StringUtils.isBlank(name))
            throw new JsonParseException("Game profile name cannot be null or blank");
    }
}
