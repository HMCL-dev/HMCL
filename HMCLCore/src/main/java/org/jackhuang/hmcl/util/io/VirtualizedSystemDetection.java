package org.jackhuang.hmcl.util.io;

import com.sun.tools.sjavac.Log;
import sun.util.logging.PlatformLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public class VirtualizedSystemDetection {
    public static boolean isChromeOSContainer(){
        String isVirtualSystemProductPath = "/sys/devices/virtual/dmi/id/";
        String isChromeOSContainerProductFile = "product_name";
        if(Files.exists(Paths.get(isVirtualSystemProductPath))){
            LOG.log(Level.INFO,"Running in Virtualized System");
            //System.out.println("Running in Virtualized System");
            try{
                String isChromeOS = new String(Files.readAllBytes(Paths.get(isVirtualSystemProductPath+isChromeOSContainerProductFile)));
                if (isChromeOS.contains("crosvm")){
                    LOG.log(Level.INFO,"Running Under Chrome OS Linux Container");
                    //System.out.println("Running Under Chrome OS Linux Container");
                    return true;
                }else {
                    return false;
                }
            }catch (IOException e){
                e.printStackTrace();
                return false;
            }
        }else {
            return false;
        }
    }
}
