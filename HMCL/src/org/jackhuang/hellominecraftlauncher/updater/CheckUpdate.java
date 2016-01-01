/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.updater;

import org.jackhuang.hellominecraftlauncher.apis.DoneListener;
import org.jackhuang.hellominecraftlauncher.download.URLGet;

/**
 *
 * @author hyh
 */
public class CheckUpdate extends Thread {
    DoneListener<UpdateRequest, Object> d;
    
    public CheckUpdate(DoneListener<UpdateRequest, Object> d) {
        this.d = d;
    }
    
    @Override
    public void run() {
        String data = URLGet.get("http://hellominecraftlauncher.duapp.com/update.php");
        while((data.charAt(0) < '0' || data.charAt(0) > '9') && data.charAt(0) != '.')
            data = data.substring(1);
        String[] req = data.split(":");
        UpdateRequest ur = null;
        if(req.length == 2) {
            String[] ver = req[0].split("\\.");
            String un = req[1];
            if(ver.length == 3) {
                byte v1, v2, v3;
                try {
                    v1 = Byte.parseByte(ver[0]);
                    v2 = Byte.parseByte(ver[1]);
                    v3 = Byte.parseByte(ver[2]);
                    ur = new UpdateRequest();
                    ur.firstVer = v1;
                    ur.secondVer = v2;
                    ur.thirdVer = v3;
                    ur.updateNote = un;
                    if(d != null)
                    {
                        d.onDone(ur, null);
                        return;
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
        d.onDone(ur, null);
    }
    
    public static void check(DoneListener<UpdateRequest, Object> d) {
        new CheckUpdate(d).start();
    }
    
}
