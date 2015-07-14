/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.svrmgr.server.schedules;

import java.util.TimerTask;
import org.jackhuang.hellominecraft.svrmgr.server.Server;
import org.jackhuang.hellominecraft.svrmgr.settings.Schedule;
/**
 *
 * @author huangyuhui
 */
public class AutoRestartSchedule extends TimerTask {
    
    Schedule main;
    Server server;
    
    public AutoRestartSchedule(Schedule s, Server s2) {
        main = s;
        server = s2;
    }

    @Override
    public void run() {
        server.restart();
    }
}
