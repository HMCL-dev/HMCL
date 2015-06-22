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
 * @author hyh
 */
public abstract class AutoSchedule extends TimerTask {
    Schedule main;
    Server server;
    
    public AutoSchedule(Schedule s, Server s2) {
        main = s;
        server = s2;
    }
}
