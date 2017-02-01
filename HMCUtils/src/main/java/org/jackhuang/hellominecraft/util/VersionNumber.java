/*
 * Hello Minecraft!.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.util;

import org.jackhuang.hellominecraft.util.log.HMCLog;

/**
 *
 * @author huangyuhui
 */
public final class VersionNumber implements Comparable<VersionNumber> {

    public final byte firstVer, secondVer, thirdVer;
    public final String version;

    public VersionNumber(byte a, byte b, byte c) {
        this(a, b, c, null);
    }

    public VersionNumber(byte a, byte b, byte c, String version) {
        firstVer = a;
        secondVer = b;
        thirdVer = c;
        this.version = version;
    }

    @Override
    public String toString() {
        return "" + firstVer + '.' + secondVer + '.' + thirdVer;
    }

    public static VersionNumber check(String data) {
        while (!data.isEmpty() && ((data.charAt(0) < '0' || data.charAt(0) > '9') && data.charAt(0) != '.'))
            data = data.substring(1);
        if (data.isEmpty())
            return null;
        VersionNumber ur;
        String[] ver = data.split("\\.");
        if (ver.length >= 3) {
            byte v1, v2, v3;
            try {
                v1 = Byte.parseByte(ver[0]);
                v2 = Byte.parseByte(ver[1]);
                v3 = Byte.parseByte(ver[2]);
                ur = new VersionNumber(v1, v2, v3, data);
                return ur;
            } catch (Exception e) {
                HMCLog.warn("Failed to parse the version", e);
            }
        }
        return null;
    }

    public static boolean isOlder(VersionNumber a, VersionNumber b) {
        if (a.firstVer < b.firstVer)
            return true;
        else if (a.firstVer == b.firstVer)
            if (a.secondVer < b.secondVer)
                return true;
            else if (a.secondVer == b.secondVer)
                if (a.thirdVer < b.thirdVer)
                    return true;
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 83 * hash + this.firstVer;
        hash = 83 * hash + this.secondVer;
        hash = 83 * hash + this.thirdVer;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final VersionNumber other = (VersionNumber) obj;
        if (this.firstVer != other.firstVer)
            return false;
        if (this.secondVer != other.secondVer)
            return false;
        if (this.thirdVer != other.thirdVer)
            return false;
        return true;
    }
    
    @Override
    public int compareTo(VersionNumber o) {
        if (isOlder(this, o))
            return -1;
        else if (isOlder(o, this))
            return 1;
        else
            return 0;
    }

}
