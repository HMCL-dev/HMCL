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
package org.jackhuang.hellominecraft.svrmgr.threads;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import org.jackhuang.hellominecraft.HMCLog;

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
        try { 
            br = new BufferedReader(new InputStreamReader(is, System.getProperty("sun.jnu.encoding", "gbk")));
        } catch (UnsupportedEncodingException ex) {
            br = new BufferedReader(new InputStreamReader(is));
        }
    }
    
    public void addListener(MonitorThreadListener l) {
        listeners.add(l);
    }
    
    @Override
    public void run() {
        String line;
        try {
            while((line = br.readLine()) != null) {
                for(MonitorThreadListener l : listeners)
                    if(l != null)
                        l.onStatus(line);
            }
        } catch (IOException ex) {
            HMCLog.warn("Failed to monitor threads.", ex);
        }
    }
    
}
