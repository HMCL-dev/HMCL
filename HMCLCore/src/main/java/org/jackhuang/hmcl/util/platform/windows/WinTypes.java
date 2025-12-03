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
package org.jackhuang.hmcl.util.platform.windows;

import com.sun.jna.*;
import com.sun.jna.ptr.ByReference;
import com.sun.jna.ptr.LongByReference;

import java.util.Arrays;
import java.util.List;

/**
 * @author Glavo
 */
public interface WinTypes {

    /// @see <a href="https://learn.microsoft.com/windows/win32/winprog/windows-data-types">Windows Data Types</a>
    final class BOOL extends IntegerType {

        public static final int SIZE = 4;

        public BOOL() {
            this(0);
        }

        public BOOL(boolean value) {
            this(value ? 1L : 0L);
        }

        public BOOL(long value) {
            super(SIZE, value, false);
            assert value == 0 || value == 1;
        }

        public boolean booleanValue() {
            return this.intValue() > 0;
        }

        @Override
        public String toString() {
            return Boolean.toString(booleanValue());
        }

    }

    /// @see <a href="https://learn.microsoft.com/windows/win32/winprog/windows-data-types">Windows Data Types</a>
    final class BOOLByReference extends ByReference {

        public BOOLByReference() {
            this(new BOOL(0));
        }

        public BOOLByReference(BOOL value) {
            super(BOOL.SIZE);
            setValue(value);
        }

        public void setValue(BOOL value) {
            getPointer().setInt(0, value.intValue());
        }

        public BOOL getValue() {
            return new BOOL(getPointer().getInt(0));
        }
    }

    /// @see <a href="https://learn.microsoft.com/windows/win32/winprog/windows-data-types">Windows Data Types</a>
    final class HANDLE extends PointerType {
        public static final long INVALID_VALUE = Native.POINTER_SIZE == 8 ? -1 : 0xFFFFFFFFL;

        public static final HANDLE INVALID = new HANDLE(Pointer.createConstant(INVALID_VALUE));

        private boolean immutable;

        public HANDLE() {
        }

        public HANDLE(Pointer p) {
            setPointer(p);
            immutable = true;
        }

        @Override
        public Object fromNative(Object nativeValue, FromNativeContext context) {
            Object o = super.fromNative(nativeValue, context);
            if (INVALID.equals(o)) {
                return INVALID;
            }
            return o;
        }

        @Override
        public void setPointer(Pointer p) {
            if (immutable) {
                throw new UnsupportedOperationException("immutable reference");
            }

            super.setPointer(p);
        }

        @Override
        public String toString() {
            return String.valueOf(getPointer());
        }
    }

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/winnt/ns-winnt-osversioninfoexw">OSVERSIONINFOEXW structure</a>
     */
    final class OSVERSIONINFOEXW extends Structure {
        public int dwOSVersionInfoSize;
        public int dwMajorVersion;
        public int dwMinorVersion;
        public int dwBuildNumber;
        public int dwPlatformId;
        public char[] szCSDVersion;
        public short wServicePackMajor;
        public short wServicePackMinor;
        public short wSuiteMask;
        public byte wProductType;
        public byte wReserved;

        public OSVERSIONINFOEXW() {
            szCSDVersion = new char[128];
            dwOSVersionInfoSize = size();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "dwOSVersionInfoSize",
                    "dwMajorVersion", "dwMinorVersion", "dwBuildNumber",
                    "dwPlatformId",
                    "szCSDVersion",
                    "wServicePackMajor", "wServicePackMinor",
                    "wSuiteMask", "wProductType",
                    "wReserved"
            );
        }
    }

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/sysinfoapi/ns-sysinfoapi-memorystatusex">MEMORYSTATUSEX structure</a>
     */
    final class MEMORYSTATUSEX extends Structure {
        public int dwLength;
        public int dwMemoryLoad;
        public long ullTotalPhys;
        public long ullAvailPhys;
        public long ullTotalPageFile;
        public long ullAvailPageFile;
        public long ullTotalVirtual;
        public long ullAvailVirtual;
        public long ullAvailExtendedVirtual;

        public MEMORYSTATUSEX() {
            dwLength = size();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "dwLength", "dwMemoryLoad",
                    "ullTotalPhys", "ullAvailPhys", "ullTotalPageFile", "ullAvailPageFile",
                    "ullTotalVirtual", "ullAvailVirtual", "ullAvailExtendedVirtual");
        }
    }

    final class GROUP_AFFINITY extends Structure {
        public LongByReference mask;
        public short group;
        public short[] reserved = new short[3];

        public GROUP_AFFINITY(Pointer memory) {
            super(memory);
        }

        public GROUP_AFFINITY() {
            super();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "mask", "group", "reserved"
            );
        }
    }

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/winnt/ns-winnt-processor_group_info">PROCESSOR_GROUP_INFO structure</a>
     */
    final class PROCESSOR_GROUP_INFO extends Structure {
        public byte maximumProcessorCount;
        public byte activeProcessorCount;
        public byte[] reserved = new byte[38];
        public LongByReference activeProcessorMask;

        public PROCESSOR_GROUP_INFO(Pointer memory) {
            super(memory);
        }

        public PROCESSOR_GROUP_INFO() {
            super();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("maximumProcessorCount", "activeProcessorCount", "reserved", "activeProcessorMask");
        }
    }

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/winnt/ns-winnt-processor_relationship">PROCESSOR_RELATIONSHIP structure</a>
     */
    final class PROCESSOR_RELATIONSHIP extends Structure {

        public byte flags;
        public byte efficiencyClass;
        public byte[] reserved = new byte[20];
        public short groupCount;
        public GROUP_AFFINITY[] groupMask = new GROUP_AFFINITY[1];

        public PROCESSOR_RELATIONSHIP() {
        }

        public PROCESSOR_RELATIONSHIP(Pointer memory) {
            super(memory);
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("flags", "efficiencyClass", "reserved", "groupCount", "groupMask");
        }

        @Override
        public void read() {
            readField("groupCount");
            if (groupCount != groupMask.length) {
                groupMask = new GROUP_AFFINITY[groupCount];
            }
            super.read();
        }
    }

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/winnt/ns-winnt-group_relationship">GROUP_RELATIONSHIP structure</a>
     */
    final class GROUP_RELATIONSHIP extends Structure {
        public short maximumGroupCount;
        public short activeGroupCount;
        public byte[] reserved = new byte[20];
        public PROCESSOR_GROUP_INFO[] groupInfo = new PROCESSOR_GROUP_INFO[1];

        public GROUP_RELATIONSHIP() {
        }

        public GROUP_RELATIONSHIP(Pointer memory) {
            super(memory);
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("maximumGroupCount", "activeGroupCount", "reserved", "groupInfo");
        }

        @Override
        public void read() {
            readField("activeGroupCount");
            if (activeGroupCount != groupInfo.length)
                groupInfo = new PROCESSOR_GROUP_INFO[activeGroupCount];
            super.read();
        }
    }
}
