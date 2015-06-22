/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import org.jackhuang.hellominecraft.HMCLog;

/**
 *
 * @author hyh
 */
public final class VersionNumber implements Comparable<VersionNumber> {

    public byte firstVer, secondVer, thirdVer;

    public VersionNumber(byte a, byte b, byte c) {
        firstVer = a; secondVer = b; thirdVer = c;
    }

    public static VersionNumber check(String data) {
        while (!data.isEmpty() && ((data.charAt(0) < '0' || data.charAt(0) > '9') && data.charAt(0) != '.')) {
            data = data.substring(1);
        }
        if (data.isEmpty()) {
            return null;
        }
        VersionNumber ur = null;
        String[] ver = data.split("\\.");
        if (ver.length == 3) {
            byte v1, v2, v3;
            try {
                v1 = Byte.parseByte(ver[0]);
                v2 = Byte.parseByte(ver[1]);
                v3 = Byte.parseByte(ver[2]);
                ur = new VersionNumber(v1, v2, v3);
                return ur;
            } catch (Exception e) {
                HMCLog.warn("Failed to parse the version", e);
            }
        }
        return null;
    }

    public static boolean isOlder(VersionNumber a, VersionNumber b) {
        if (a.firstVer < b.firstVer) {
            return true;
        } else if (a.firstVer == b.firstVer) {
            if (a.secondVer < b.secondVer) {
                return true;
            } else if (a.secondVer == b.secondVer) {
                if (a.thirdVer < b.thirdVer) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int compareTo(VersionNumber o) {
        if(isOlder(this, o)) return -1;
        else if(isOlder(o, this)) return 1;
        else return 0;
    }

}
