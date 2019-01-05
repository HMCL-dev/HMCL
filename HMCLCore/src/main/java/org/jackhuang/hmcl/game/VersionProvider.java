/**
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
package org.jackhuang.hmcl.game;

/**
 * Supports version accessing.
 *
 * @see Version#resolve
 * @author huangyuhui
 */
public interface VersionProvider {

    /**
     * Does the version of id exist?
     *
     * @param id the id of version
     * @return true if the version exists
     */
    boolean hasVersion(String id);

    /**
     * Get the version
     *
     * @param id the id of version
     * @return the version you want
     */
    Version getVersion(String id) throws VersionNotFoundException;
}
