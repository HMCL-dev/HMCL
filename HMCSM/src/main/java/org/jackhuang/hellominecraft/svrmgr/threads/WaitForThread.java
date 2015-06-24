/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.threads;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.jackhuang.hellominecraft.utils.EventHandler;

/**
 *
 * @author jack
 */
public class WaitForThread extends Thread {
    
    public final EventHandler<Integer> event = new EventHandler<>(this);
    Process p;
    
    public WaitForThread(Process p) {
        this.p = p;
    }
    
    @Override
    public void run() {
        try {
            int exitCode = p.waitFor();
            event.execute(exitCode);
        } catch (InterruptedException ex) {
            Logger.getLogger(WaitForThread.class.getName()).log(Level.SEVERE, null, ex);
            event.execute(-1);
        }
    }
    
}
