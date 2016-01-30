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
package org.jackhuang.hellominecraft.svrmgr.server.schedule;

import java.util.TimerTask;
import org.jackhuang.hellominecraft.svrmgr.server.Server;
import org.jackhuang.hellominecraft.svrmgr.server.BackupManager;
import org.jackhuang.hellominecraft.svrmgr.setting.Schedule;

/**
 *
 * @author huangyuhui
 */
public class AutoBackupSchedule extends TimerTask {

    Schedule main;
    Server server;

    public AutoBackupSchedule(Schedule s, Server s2) {
        main = s;
        server = s2;
    }

    @Override
    public void run() {
        System.out.println("Backup");
        Server.getInstance().sendCommand("say 自动备份完毕");
        BackupManager.backupAllWorlds();
    }

}
