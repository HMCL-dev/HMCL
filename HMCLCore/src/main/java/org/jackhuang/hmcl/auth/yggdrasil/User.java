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
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.Validation;

/**
 *
 * @author huang
 */
public final class User implements Validation {

    private final String id;
    private final PropertyMap properties;

    public User(String id) {
        this(id, null);
    }

    public User(String id, PropertyMap properties) {
        this.id = id;
        this.properties = properties;
    }

    public String getId() {
        return id;
    }

    public PropertyMap getProperties() {
        return properties;
    }

    @Override
    public void validate() throws JsonParseException {
        if (StringUtils.isBlank(id))
            throw new JsonParseException("User id cannot be empty.");
    }

}
