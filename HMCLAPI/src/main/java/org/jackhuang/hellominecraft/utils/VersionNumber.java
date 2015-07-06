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
package org.jackhuang.hellominecraft.utils;

import org.jackhuang.hellominecraft.HMCLog;

/**
 *
 * @author huangyuhui
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
        VersionNumber ur;
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
