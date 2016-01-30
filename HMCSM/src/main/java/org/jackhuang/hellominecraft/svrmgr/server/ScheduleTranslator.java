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
package org.jackhuang.hellominecraft.svrmgr.server;

import org.jackhuang.hellominecraft.svrmgr.server.schedule.AutoBackupSchedule;
import org.jackhuang.hellominecraft.svrmgr.server.schedule.AutoRestartSchedule;
import org.jackhuang.hellominecraft.svrmgr.server.schedule.AutoSaveSchedule;
import org.jackhuang.hellominecraft.svrmgr.server.schedule.AutoBroadcastSchedule;
import java.util.TimerTask;
import org.jackhuang.hellominecraft.svrmgr.setting.Schedule;

/**
 *
 * @author huangyuhui
 */
public class ScheduleTranslator {

    public static TimerTask translate(Server ser, Schedule s) {
        switch (s.type) {
        case Schedule.TYPE_AUTO_SAVE:
            return new AutoSaveSchedule(s, ser);
        case Schedule.TYPE_AUTO_RESTART:
            return new AutoRestartSchedule(s, ser);
        case Schedule.TYPE_AUTO_BACKUP:
            return new AutoBackupSchedule(s, ser);
        case Schedule.TYPE_AUTO_BROADCAST:
            return new AutoBroadcastSchedule(s, ser);
        case Schedule.TYPE_AUTO_SEND_COMMAND:
            return new AutoBroadcastSchedule(s, ser);
        }
        return null;
    }

    public static String getName(Schedule s) {
        switch (s.type) {
        case Schedule.TYPE_AUTO_SAVE:
            return "自动保存";
        case Schedule.TYPE_AUTO_RESTART:
            return "自动重启";
        case Schedule.TYPE_AUTO_BACKUP:
            return "自动备份";
        case Schedule.TYPE_AUTO_BROADCAST:
            return "自动广播";
        case Schedule.TYPE_AUTO_SEND_COMMAND:
            return "自动发送命令";
        }
        return "";
    }

    public static String getTimeTypeName(Schedule s) {
        switch (s.timeType) {
        case Schedule.TIME_TYPE_PER:
            return "每x分钟";
        case Schedule.TIME_TYPE_PAST_HOUR:
            return "整点后x分钟";
        case Schedule.TIME_TYPE_SERVER_STARTED:
            return "当服务器启动";
        case Schedule.TIME_TYPE_SERVER_STOPPED:
            return "当服务器关闭";
        case Schedule.TIME_TYPE_SERVER_CRASHED:
            return "当服务器崩溃";
        }
        return "";
    }

    public static Object[] getRow(Schedule s) {
        return new Object[] {
            getName(s), getTimeTypeName(s), s.per, s.content
        };
    }

}
