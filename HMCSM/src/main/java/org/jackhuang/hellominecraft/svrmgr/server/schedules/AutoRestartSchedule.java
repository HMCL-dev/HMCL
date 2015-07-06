/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
