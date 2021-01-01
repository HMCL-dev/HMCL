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
package org.jackhuang.hmcl.auth;

import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;

import java.util.List;

/**
 * This interface is for your application to open a GUI for user to choose the character
 * when a having-multi-character yggdrasil account is being logging in.
 */
public interface CharacterSelector {

    /**
     * Select one of {@code names} GameProfiles to login.
     * @param names available game profiles.
     * @throws NoSelectedCharacterException if cannot select any character may because user close the selection window or cancel the selection.
     * @return your choice of game profile.
     */
    GameProfile select(YggdrasilService yggdrasilService, List<GameProfile> names) throws NoSelectedCharacterException;

}
