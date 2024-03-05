package org.jackhuang.hmcl.util.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class VirtualizedSystemDetection {
    private VirtualizedSystemDetection() {
        throw new UnsupportedOperationException();
    }
    
    public static boolean isChromeOSContainer() {
        String isChromeOSContainerProductFile = "/sys/devices/virtual/dmi/id/product_name";
        try {
            String isChromeOS = new String(Files.readAllBytes(Paths.get(isChromeOSContainerProductFile)));
            if (isChromeOS.contains("crosvm")) {
                //LOG.log(Level.INFO,"Running Under Chrome OS Linux Container");
                System.out.println("Running Under Chrome OS Linux Container");
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
