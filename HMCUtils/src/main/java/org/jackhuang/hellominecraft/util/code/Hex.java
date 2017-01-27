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

public final class Hex {

    private static final char[] DIGITS_LOWER = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    private static final char[] DIGITS_UPPER = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
    private final Charset charset;

    public static byte[] decodeHex(char[] data) throws Exception {
        int len = data.length;

        if ((len & 0x1) != 0)
            throw new Exception("Odd number of characters.");

        byte[] out = new byte[len >> 1];

        int i = 0;
        for (int j = 0; j < len; i++) {
            int f = toDigit(data[j], j) << 4;
            j++;
            f |= toDigit(data[j], j);
            j++;
            out[i] = (byte) (f & 0xFF);
        }

        return out;
    }

    public static char[] encodeHex(byte[] data) {
        return encodeHex(data, true);
    }

    public static char[] encodeHex(byte[] data, boolean toLowerCase) {
        return encodeHex(data, toLowerCase ? DIGITS_LOWER : DIGITS_UPPER);
    }

    protected static char[] encodeHex(byte[] data, char[] toDigits) {
        int l = data.length;
        char[] out = new char[l << 1];

        int i = 0;
        for (int j = 0; i < l; i++) {
            out[(j++)] = toDigits[((0xF0 & data[i]) >>> 4)];
            out[(j++)] = toDigits[(0xF & data[i])];
        }
        return out;
    }

    public static String encodeHexString(byte[] data) {
        return new String(encodeHex(data));
    }

    protected static int toDigit(char ch, int index) {
        int digit = Character.digit(ch, 16);
        if (digit == -1)
            throw new IllegalArgumentException("Illegal hexadecimal character " + ch + " at index " + index);
        return digit;
    }

    public Hex() {
        this(Charsets.DEFAULT_CHARSET);
    }

    public Hex(Charset charset) {
        this.charset = charset;
    }

    public byte[] decode(byte[] array) throws Exception {
        return decodeHex(new String(array, getCharset()).toCharArray());
    }

    public Object decode(Object object) throws Exception {
        try {
            char[] charArray = (object instanceof String) ? ((String) object).toCharArray() : (char[]) (char[]) object;
            return decodeHex(charArray);
        } catch (ClassCastException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    public byte[] encode(byte[] array) {
        return encodeHexString(array).getBytes(getCharset());
    }

    public Object encode(Object object)
        throws Exception {
        try {
            byte[] byteArray = (object instanceof String) ? ((String) object).getBytes(getCharset()) : (byte[]) (byte[]) object;

            return encodeHex(byteArray);
        } catch (ClassCastException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    public Charset getCharset() {
        return this.charset;
    }

    public String getCharsetName() {
        return this.charset.name();
    }

    @Override
    public String toString() {
        return super.toString() + "[charsetName=" + this.charset + "]";
    }
}
