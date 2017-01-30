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
package org.jackhuang.hellominecraft.util.code;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import org.jackhuang.hellominecraft.util.sys.IOUtils;

public final class Charsets {
    
    private Charsets() {
    }

    /*public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

    public static final Charset US_ASCII = Charset.forName("US-ASCII");

    public static final Charset UTF_16 = Charset.forName("UTF-16");

    public static final Charset UTF_16BE = Charset.forName("UTF-16BE");

    public static final Charset UTF_16LE = Charset.forName("UTF-16LE");*/

    public static final Charset UTF_8 = Charset.forName("UTF-8");
    
    public static final Charset DEFAULT_CHARSET = UTF_8;

    public static Charset toCharset(String charset) {
        if (charset == null) return Charset.defaultCharset();
        try {
            return Charset.forName(charset);
        } catch(UnsupportedCharsetException ignored) {
            return Charset.defaultCharset();
        }
    }
    
    public static Charset toCharset() {
        return toCharset(System.getProperty("sun.jnu.encoding", IOUtils.DEFAULT_CHARSET));
    }
}
