/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.server.schedules;

import java.io.IOException;
import java.util.TimerTask;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.svrmgr.server.Server;
import org.jackhuang.hellominecraft.svrmgr.settings.Schedule;

/**
 *
 * @author hyh
 */
public class AutoExecuteSchedule extends TimerTask {
    Schedule main;
    Server server;
    
    public AutoExecuteSchedule(Schedule s, Server s2) {
        main = s;
        server = s2;
    }

    @Override
    public void run() {
        try {
            Runtime.getRuntime().exec(main.content);
        } catch (IOException ex) {
            HMCLog.err("Failed to exec command: " + main.content, ex);
        }
    }
}
