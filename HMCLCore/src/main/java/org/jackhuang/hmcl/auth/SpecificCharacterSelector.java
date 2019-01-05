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
package org.jackhuang.hmcl.auth;

import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;

import java.util.List;
import java.util.UUID;

/**
 * Select character by name.
 */
public class SpecificCharacterSelector implements CharacterSelector {
    private UUID uuid;

    /**
     * Constructor.
     * @param uuid character's uuid.
     */
    public SpecificCharacterSelector(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public GameProfile select(Account account, List<GameProfile> names) throws NoSelectedCharacterException {
        return names.stream().filter(profile -> profile.getId().equals(uuid)).findAny().orElseThrow(() -> new NoSelectedCharacterException(account));
    }
}
