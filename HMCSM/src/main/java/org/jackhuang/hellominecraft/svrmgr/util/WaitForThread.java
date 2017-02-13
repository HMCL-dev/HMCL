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
package org.jackhuang.hellominecraft.svrmgr.util;

import org.jackhuang.hellominecraft.api.EventHandler;
import org.jackhuang.hellominecraft.api.SimpleEvent;
import org.jackhuang.hellominecraft.util.log.HMCLog;

/**
 *
 * @author jack
 */
public class WaitForThread extends Thread {

    public final EventHandler<SimpleEvent<Integer>> event = new EventHandler<>();
    Process p;

    public WaitForThread(Process p) {
        this.p = p;
    }

    @Override
    public void run() {
        try {
            int exitCode = p.waitFor();
            event.fire(new SimpleEvent<>(this, exitCode));
        } catch (InterruptedException ex) {
            HMCLog.err("Game has been interrupted.", ex);
            event.fire(new SimpleEvent<>(this, -1));
        }
    }

}
