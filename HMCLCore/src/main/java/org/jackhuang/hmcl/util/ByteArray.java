/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util;

import java.lang.invoke.VarHandle;

import static java.lang.invoke.MethodHandles.byteArrayViewVarHandle;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

/**
 * @author Glavo
 */
public final class ByteArray {
    private static final VarHandle SHORT_LE = byteArrayViewVarHandle(short[].class, LITTLE_ENDIAN);
    private static final VarHandle INT_LE = byteArrayViewVarHandle(int[].class, LITTLE_ENDIAN);
    private static final VarHandle LONG_LE = byteArrayViewVarHandle(long[].class, LITTLE_ENDIAN);

    private static final VarHandle SHORT_BE = byteArrayViewVarHandle(short[].class, BIG_ENDIAN);
    private static final VarHandle INT_BE = byteArrayViewVarHandle(int[].class, BIG_ENDIAN);
    private static final VarHandle LONG_BE = byteArrayViewVarHandle(long[].class, BIG_ENDIAN);

    // Get

    public static byte getByte(byte[] array, int offset) {
        return array[offset];
    }

    public static int getUnsignedByte(byte[] array, int offset) {
        return Byte.toUnsignedInt(getByte(array, offset));
    }

    public static short getShortLE(byte[] array, int offset) {
        return (short) SHORT_LE.get(array, offset);
    }

    public static int getUnsignedShortLE(byte[] array, int offset) {
        return Short.toUnsignedInt(getShortLE(array, offset));
    }

    public static short getShortBE(byte[] array, int offset) {
        return (short) SHORT_BE.get(array, offset);
    }

    public static int getUnsignedShortBE(byte[] array, int offset) {
        return Short.toUnsignedInt(getShortBE(array, offset));
    }

    public static int getIntLE(byte[] array, int offset) {
        return (int) INT_LE.get(array, offset);
    }

    public static long getUnsignedIntLE(byte[] array, int offset) {
        return Integer.toUnsignedLong(getIntLE(array, offset));
    }

    public static int getIntBE(byte[] array, int offset) {
        return (int) INT_BE.get(array, offset);
    }

    public static long getUnsignedIntBE(byte[] array, int offset) {
        return Integer.toUnsignedLong(getIntBE(array, offset));
    }

    public static long getLongLE(byte[] array, int offset) {
        return (long) LONG_LE.get(array, offset);
    }

    public static long getLongBE(byte[] array, int offset) {
        return (long) LONG_BE.get(array, offset);
    }

    // Set

    public static void setByte(byte[] array, int offset, byte value) {
        array[offset] = value;
    }

    public static void setUnsignedByte(byte[] array, int offset, int value) {
        array[offset] = (byte) (value & 0xff);
    }

    public static void setShortLE(byte[] array, int offset, short value) {
        SHORT_LE.set(array, offset, value);
    }

    public static void setUnsignedShortLE(byte[] array, int offset, int value) {
        setShortLE(array, offset, (short) (value & 0xffff));
    }

    public static void setShortBE(byte[] array, int offset, short value) {
        SHORT_BE.set(array, offset, value);
    }

    public static void setUnsignedShortBE(byte[] array, int offset, int value) {
        setShortBE(array, offset, (short) (value & 0xffff));
    }

    public static void setIntLE(byte[] array, int offset, int value) {
        INT_LE.set(array, offset, value);
    }

    public static void setUnsignedIntLE(byte[] array, int offset, long value) {
        setIntLE(array, offset, (int) (value & 0xffff_ffffL));
    }

    public static void setIntBE(byte[] array, int offset, int value) {
        INT_BE.set(array, offset, value);
    }

    public static void setUnsignedIntBE(byte[] array, int offset, long value) {
        setIntBE(array, offset, (int) (value & 0xffff_ffffL));
    }

    public static void setLongLE(byte[] array, int offset, long value) {
        LONG_LE.set(array, offset, value);
    }

    public static void setLongBE(byte[] array, int offset, long value) {
        LONG_BE.set(array, offset, value);
    }

    private ByteArray() {
    }
}
