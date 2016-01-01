/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraftlauncher.apis.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jackhuang.hellominecraftlauncher.apis.HMCLLog;

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
            HMCLLog.err("Failed to get md5", e);
            return "";
        }
    }
}
