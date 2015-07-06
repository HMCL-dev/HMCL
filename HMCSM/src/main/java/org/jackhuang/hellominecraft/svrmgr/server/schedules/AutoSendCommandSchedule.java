/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.server.schedules;

import org.jackhuang.hellominecraft.svrmgr.server.Server;
import org.jackhuang.hellominecraft.svrmgr.settings.Schedule;

/**
 *
 * @author huangyuhui
 */
public class AutoSendCommandSchedule extends AutoSchedule {

    public AutoSendCommandSchedule(Schedule s, Server s2) {
        super(s, s2);
    }

    @Override
    public void run() {
        server.sendCommand(main.content);
    }
}
