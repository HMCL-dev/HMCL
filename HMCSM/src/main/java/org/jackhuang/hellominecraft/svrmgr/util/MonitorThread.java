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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import org.jackhuang.hellominecraft.util.code.Charsets;
import org.jackhuang.hellominecraft.util.log.HMCLog;

/**
 *
 * @author huangyuhui
 */
public class MonitorThread extends Thread {

    public interface MonitorThreadListener {

        void onStatus(String status);
    }

    InputStream is;
    BufferedReader br;
    ArrayList<MonitorThreadListener> listeners;

    public MonitorThread(InputStream is) {
        this.listeners = new ArrayList<>(5);
        br = new BufferedReader(new InputStreamReader(is, Charsets.toCharset(System.getProperty("sun.jnu.encoding", "gbk"))));
    }

    public void addListener(MonitorThreadListener l) {
        listeners.add(l);
    }

    @Override
    public void run() {
        String line;
        try {
            while ((line = br.readLine()) != null)
                for (MonitorThreadListener l : listeners)
                    if (l != null)
                        l.onStatus(line);
        } catch (IOException ex) {
            HMCLog.warn("Failed to monitor threads.", ex);
        }
    }

}
