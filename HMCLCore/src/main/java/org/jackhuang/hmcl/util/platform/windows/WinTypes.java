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
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author Glavo
 */
public interface WinTypes {

    /// A 128-bit Windows globally unique identifier.
    ///
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/guiddef/ns-guiddef-guid">GUID structure</a>
    final class GUID extends Structure {

        /// The `IPropertyStore` interface identifier.
        ///
        /// Callers must treat this constant as read-only.
        ///
        /// @see <a href="https://learn.microsoft.com/windows/win32/api/propsys/nn-propsys-ipropertystore">IPropertyStore</a>
        public static final GUID IID_IPropertyStore = of(
                0x886D8EEB, (short) 0x8CF2, (short) 0x4446,
                (byte) 0x8D, (byte) 0x02, (byte) 0xCD, (byte) 0xBA,
                (byte) 0x1D, (byte) 0xBD, (byte) 0xCF, (byte) 0x99
        );

        /// The first 32 bits of the GUID.
        public int Data1;

        /// The next 16 bits of the GUID.
        public short Data2;

        /// The next 16 bits of the GUID.
        public short Data3;

        /// The remaining 8 bytes of the GUID.
        public byte[] Data4 = new byte[8];

        /// Creates an empty GUID structure.
        public GUID() {
        }

        /// Creates a GUID over an existing native memory region.
        ///
        /// @param memory the native memory containing a GUID
        public GUID(Pointer memory) {
            super(memory);
            read();
        }

        /// Creates a GUID from its canonical integer and byte components.
        ///
        /// @param data1 the first 32 bits
        /// @param data2 the next 16 bits
        /// @param data3 the next 16 bits
        /// @param data4 the remaining 8 bytes
        /// @return a populated GUID structure
        public static GUID of(int data1, short data2, short data3, byte... data4) {
            if (data4.length != 8) {
                throw new IllegalArgumentException("GUID Data4 must contain exactly 8 bytes");
            }
            GUID guid = new GUID();
            guid.Data1 = data1;
            guid.Data2 = data2;
            guid.Data3 = data3;
            System.arraycopy(data4, 0, guid.Data4, 0, 8);
            guid.write();
            return guid;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("Data1", "Data2", "Data3", "Data4");
        }
    }

    /// Identifies a property inside a property set.
    ///
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/wtypes/ns-wtypes-propertykey">PROPERTYKEY structure</a>
    final class PROPERTYKEY extends Structure {

        /// Format identifier shared by `System.AppUserModel.*` properties.
        private static final GUID FMTID_AppUserModel = GUID.of(
                0x9F4C2855, (short) 0x9F79, (short) 0x4B39,
                (byte) 0xA8, (byte) 0xD0, (byte) 0xE1, (byte) 0xD4,
                (byte) 0x2D, (byte) 0xE1, (byte) 0xD5, (byte) 0xF3
        );

        /// The command line used to relaunch the application from the taskbar.
        ///
        /// Callers must treat this constant as read-only.
        ///
        /// @see <a href="https://learn.microsoft.com/windows/win32/properties/props-system-appusermodel-relaunchcommand">System.AppUserModel.RelaunchCommand</a>
        public static final PROPERTYKEY PKEY_AppUserModel_RelaunchCommand = of(FMTID_AppUserModel, 2);

        /// The icon resource shown for the application's taskbar button and pinned items.
        ///
        /// Callers must treat this constant as read-only.
        ///
        /// @see <a href="https://learn.microsoft.com/windows/win32/properties/props-system-appusermodel-relaunchiconresource">System.AppUserModel.RelaunchIconResource</a>
        public static final PROPERTYKEY PKEY_AppUserModel_RelaunchIconResource = of(FMTID_AppUserModel, 3);

        /// The display name used when the application is relaunched from the taskbar.
        ///
        /// Callers must treat this constant as read-only.
        ///
        /// @see <a href="https://learn.microsoft.com/windows/win32/properties/props-system-appusermodel-relaunchdisplaynameresource">System.AppUserModel.RelaunchDisplayNameResource</a>
        public static final PROPERTYKEY PKEY_AppUserModel_RelaunchDisplayNameResource = of(FMTID_AppUserModel, 4);

        /// The AppUserModelID property key used with window and shortcut property stores.
        ///
        /// Callers must treat this constant as read-only.
        ///
        /// @see <a href="https://learn.microsoft.com/windows/win32/properties/props-system-appusermodel-id">System.AppUserModel.ID</a>
        public static final PROPERTYKEY PKEY_AppUserModel_ID = of(FMTID_AppUserModel, 5);

        /// The format identifier of the property set that contains the property.
        public GUID fmtid = new GUID();

        /// The property identifier within the property set.
        public int pid;

        /// Creates an empty property key.
        public PROPERTYKEY() {
        }

        /// Creates a property key over an existing native memory region.
        ///
        /// @param memory the native memory containing a PROPERTYKEY
        public PROPERTYKEY(Pointer memory) {
            super(memory);
            read();
        }

        /// Creates a property key from a format identifier and property identifier.
        ///
        /// @param fmtid the property set format identifier
        /// @param pid the property identifier within that set
        /// @return a populated property key
        public static PROPERTYKEY of(GUID fmtid, int pid) {
            PROPERTYKEY key = new PROPERTYKEY();
            key.fmtid.Data1 = fmtid.Data1;
            key.fmtid.Data2 = fmtid.Data2;
            key.fmtid.Data3 = fmtid.Data3;
            System.arraycopy(fmtid.Data4, 0, key.fmtid.Data4, 0, 8);
            key.pid = pid;
            key.write();
            return key;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("fmtid", "pid");
        }
    }

    /// Minimal `PROPVARIANT` layout for values HMCL writes with [`IPropertyStore#SetValue`].
    ///
    /// Only `VT_EMPTY` and `VT_LPWSTR` construction is supported. The native layout is preserved:
    /// an 8-byte header followed by the value union, which is 8 bytes on x86
    /// (`sizeof(PROPVARIANT) == 16`) and 16 bytes on x64 (`sizeof(PROPVARIANT) == 24`).
    /// This type must not be used as an output buffer for APIs that write arbitrary
    /// `PROPVARIANT` values (for example `IPropertyStore::GetValue`).
    ///
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/propidlbase/ns-propidlbase-propvariant">PROPVARIANT structure</a>
    final class PROPVARIANT extends Structure {

        /// Native `sizeof(PROPVARIANT)`: `16` on x86, `24` on x64.
        public static final int SIZE = 8 + (Native.POINTER_SIZE == 8 ? 16 : 8);

        /// Byte offset of the value union (`pwszVal` for `VT_LPWSTR`).
        private static final int UNION_OFFSET = 8;

        /// The variant type tag, such as [`WinConstants#VT_EMPTY`] or [`WinConstants#VT_LPWSTR`].
        public short vt;

        /// Reserved; must be zero for values constructed by HMCL.
        public short wReserved1;

        /// Reserved; must be zero for values constructed by HMCL.
        public short wReserved2;

        /// Reserved; must be zero for values constructed by HMCL.
        public short wReserved3;

        /// Backing storage for the native value union.
        ///
        /// Sized to the full ABI union width so [`#size()`] matches `sizeof(PROPVARIANT)`.
        public byte[] unionData = new byte[SIZE - UNION_OFFSET];

        /// Keeps caller-owned wide-string memory reachable while this variant is in use.
        private @Nullable Memory ownedStringMemory;

        /// Creates an empty property variant (`VT_EMPTY`).
        public PROPVARIANT() {
            super(ALIGN_NONE);
        }

        /// Creates a property variant over an existing native memory region.
        ///
        /// @param memory the native memory containing a PROPVARIANT
        public PROPVARIANT(Pointer memory) {
            super(memory, ALIGN_NONE);
            read();
        }

        /// Sets this variant to a null-terminated UTF-16 string value (`VT_LPWSTR`).
        ///
        /// The string memory is owned by this instance for the duration of native calls made
        /// while the variant remains configured. The property store copies the value during
        /// [`IPropertyStore#SetValue(PROPERTYKEY, PROPVARIANT)`], so the instance must stay
        /// alive until that call returns.
        ///
        /// @param string the string value to store
        public void setStringValue(String string) {
            Memory memory = new Memory(((long) string.length() + 1L) * Native.WCHAR_SIZE);
            memory.setWideString(0, string);
            this.ownedStringMemory = memory;
            this.vt = (short) WinConstants.VT_LPWSTR;
            this.wReserved1 = 0;
            this.wReserved2 = 0;
            this.wReserved3 = 0;
            Arrays.fill(this.unionData, (byte) 0);
            write();
        }

        /// Resets this variant to `VT_EMPTY`.
        ///
        /// Use this when removing a window property with
        /// [`IPropertyStore#SetValue(PROPERTYKEY, PROPVARIANT)`].
        public void setEmpty() {
            this.ownedStringMemory = null;
            this.vt = (short) WinConstants.VT_EMPTY;
            this.wReserved1 = 0;
            this.wReserved2 = 0;
            this.wReserved3 = 0;
            Arrays.fill(this.unionData, (byte) 0);
            write();
        }

        /// Writes the structure, then plants `pwszVal` when this instance owns a string value.
        ///
        /// JNA field writing alone cannot place a pointer into the leading bytes of
        /// [`#unionData`] without a second pass.
        @Override
        public void write() {
            super.write();
            if (ownedStringMemory != null && vt == WinConstants.VT_LPWSTR) {
                getPointer().setPointer(UNION_OFFSET, ownedStringMemory);
            }
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("vt", "wReserved1", "wReserved2", "wReserved3", "unionData");
        }
    }

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
