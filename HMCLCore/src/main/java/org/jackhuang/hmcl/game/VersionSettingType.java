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
package org.jackhuang.hmcl.game;

/**
 * 
 * @author yaoxi-std
 */
public enum VersionSettingType {

    /**
     * If this is an unknown version,
     * then it contains properties that
     * both client and server setting
     * should contain.
     * 
     * This happens when we use clone()
     * method from a global version, or
     * we have detected a new version.
     * 
     * This is the default value
     */
    UNKNOWN_VERSION,

    /**
     * Is it a client or a server?
     */
    CLIENT_VERSION,
    SERVER_VERSION,

    /**
     * If it is a global version setting,
     * then it contains both client and
     * server setting.
     */
    GLOBAL_VERSION
}
