/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.threads;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jackhuang.hellominecraft.DoneListener1;

/**
 *
 * @author jack
 */
public class WaitForThread extends Thread {
    
    public ArrayList<DoneListener1<Integer>> al;
    Process p;
    
    public WaitForThread(Process p) {
        this.p = p;
        al = new ArrayList<DoneListener1<Integer>>();
    }
    
    public void addListener(DoneListener1<Integer> dl) {
        al.add(dl);
    }
    
    @Override
    public void run() {
        try {
            int exitCode = p.waitFor();
            for(DoneListener1<Integer> dl : al)
                if(dl != null)
                    dl.onDone(exitCode);
        } catch (InterruptedException ex) {
            Logger.getLogger(WaitForThread.class.getName()).log(Level.SEVERE, null, ex);
            for(DoneListener1<Integer> dl : al)
                if(dl != null)
                    dl.onDone(-1);
        }
    }
    
}
