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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jackhuang.hellominecraft.HMCLog;

/**
 *
 * @author huang
 */
public class MD5Utils {
    
    private static final char e[] = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f'
    };

    public static String hash(String type, String source) {
        try {
            StringBuilder stringbuilder;
            MessageDigest md = MessageDigest.getInstance(type);
            md.update(source.getBytes());
            byte[] bytes = md.digest();
            int s2 = bytes.length;
            stringbuilder = new StringBuilder(s2 << 1);
            for (int i1 = 0; i1 < s2; i1++) {
                stringbuilder.append(e[bytes[i1] >> 4 & 0xf]);
                stringbuilder.append(e[bytes[i1] & 0xf]);
            }

            return stringbuilder.toString();
        } catch (NoSuchAlgorithmException e) {
            HMCLog.err("Failed to get md5", e);
            return "";
        }
    }
}
