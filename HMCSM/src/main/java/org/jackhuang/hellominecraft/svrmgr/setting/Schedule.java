/*
 * Hello Minecraft! Server Manager.
 * Copyright (C) 2013  huangyuhui
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
package org.jackhuang.hellominecraft.svrmgr.setting;

/**
 *
 * @author huangyuhui
 */
public class Schedule {

    public static final int TYPE_AUTO_SAVE = 0,
        TYPE_AUTO_RESTART = 1,
        TYPE_AUTO_BACKUP = 2,
        TYPE_AUTO_BROADCAST = 3,
        TYPE_AUTO_SEND_COMMAND = 4,
        TYPE_AUTO_EXECUTE = 5;
    public static final int TYPE2_AUTO_BACKUP_PLUGINS = 1,
        TYPE2_AUTH_BACKUP_CONFIG = 2,
        TYPE3_AUTH_BACKUP_WORLD = 3;
    public static final int TIME_TYPE_PER = 0,
        TIME_TYPE_PAST_HOUR = 1,
        TIME_TYPE_SERVER_STARTED = 2,
        TIME_TYPE_SERVER_STOPPED = 3,
        TIME_TYPE_SERVER_CRASHED = 4;

    public int type, type2, timeType;
    public String content;
    public double per;
}
