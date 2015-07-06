/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
        this.listeners = new ArrayList<MonitorThreadListener>(5);
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
