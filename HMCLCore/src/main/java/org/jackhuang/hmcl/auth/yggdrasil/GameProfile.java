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

import java.util.Objects;
import java.util.UUID;

import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.gson.Validation;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;

/**
 * @author huangyuhui
 */
@Immutable
public class GameProfile implements Validation {

    @JsonAdapter(UUIDTypeAdapter.class)
    private final UUID id;

    private final String name;

    public GameProfile(UUID id, String name) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public void validate() throws JsonParseException {
        Validation.requireNonNull(id, "Game profile id cannot be null");
        Validation.requireNonNull(name, "Game profile name cannot be null");
    }
}
