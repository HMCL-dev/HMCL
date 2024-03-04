package org.jackhuang.hmcl.util.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class VirtualizedSystemDetection {
    private VirtualizedSystemDetection() {
        throw new UnsupportedOperationException();
    }
    public static boolean isChromeOSContainer() {
        String isVirtualSystemProductPath = "/sys/devices/virtual/dmi/id/";
        String isChromeOSContainerProductFile = "product_name";
        if (Files.exists(Paths.get(isVirtualSystemProductPath))) {
            try {
                String isChromeOS = new String(Files.readAllBytes(Paths.get(isVirtualSystemProductPath + isChromeOSContainerProductFile)));
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
        } else {
            return false;
        }
    }
}
