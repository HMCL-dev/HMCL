/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Representing a file entry which allow modpack developer declare it's optional or not
 * */
public interface ModpackFile {

    /**
     * Get the file name for the file
     */
    String getFileName();

    /**
     * Return if the file optional on the client side
     */
    boolean isOptional();

    /**
     * Return the path of the file
     */
    String getPath();
    
    /**
     * Return the mod the file belongs to
     * <p>
     * About null and Optional.empty():
     * <ul>
     * <li> If the file hasn't been queried from remote, the mod will be null </li>
     * <li> If the file has been queried from remote but not found, the mod will be Optional.empty() </li>
     * </ul>
     * </p>
     */
    @Nullable Optional<RemoteMod> getMod();
}
