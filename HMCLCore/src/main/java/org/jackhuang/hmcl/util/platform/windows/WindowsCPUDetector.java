// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.HardwareVendor;
import org.jetbrains.annotations.Nullable;

/**
 * @author Glavo
 */
final class WindowsCPUDetector {

    private static void detectName(CentralProcessor.Builder builder, WinReg reg) {
        Object name = reg.queryValue(WinReg.HKEY.HKEY_LOCAL_MACHINE, "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "ProcessorNameString");
        Object vendor = reg.queryValue(WinReg.HKEY.HKEY_LOCAL_MACHINE, "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "VendorIdentifier");

        if (name instanceof String)
            builder.setName(CentralProcessor.cleanName((String) name));

        if (vendor instanceof String)
            builder.setVendor(HardwareVendor.of((String) vendor));
    }

    private static void detectCores(CentralProcessor.Builder builder, Kernel32 kernel32) {
        int coresLogical = 0;
        int coresPhysical = 0;
        int packages = 0;

        IntByReference length = new IntByReference();
        if (!kernel32.GetLogicalProcessorInformationEx(WinConstants.RelationAll, null, length) && length.getValue() == 0)
            throw new AssertionError("Failed to get logical processor information length: " + kernel32.GetLastError());

        try (Memory pProcessorInfo = new Memory(Integer.toUnsignedLong(length.getValue()))) {
            if (!kernel32.GetLogicalProcessorInformationEx(WinConstants.RelationAll, pProcessorInfo, length))
                throw new AssertionError("Failed to get logical processor information length: " + kernel32.GetLastError());

            for (long offset = 0L; offset < pProcessorInfo.size(); ) {
                int relationship = pProcessorInfo.getInt(offset);
                long size = Integer.toUnsignedLong(pProcessorInfo.getInt(offset + 4L));

                if (relationship == WinConstants.RelationGroup) {
                    WinTypes.GROUP_RELATIONSHIP groupRelationship = new WinTypes.GROUP_RELATIONSHIP(pProcessorInfo.share(offset + 8L, size - 8L));
                    groupRelationship.read();

                    int activeGroupCount = Short.toUnsignedInt(groupRelationship.activeGroupCount);
                    for (int i = 0; i < activeGroupCount; i++) {
                        coresLogical += Short.toUnsignedInt(groupRelationship.groupInfo[i].maximumProcessorCount);
                    }
                } else if (relationship == WinConstants.RelationProcessorCore)
                    coresPhysical++;
                else if (relationship == WinConstants.RelationProcessorPackage)
                    packages++;

                offset += size;
            }
        }

        builder.setCores(new CentralProcessor.Cores(coresPhysical, coresLogical, packages));
    }

    static @Nullable CentralProcessor detect() {
        WinReg reg = WinReg.INSTANCE;
        Kernel32 kernel32 = Kernel32.INSTANCE;
        if (reg == null)
            return null;

        CentralProcessor.Builder builder = new CentralProcessor.Builder();
        detectName(builder, reg);
        if (kernel32 != null)
            detectCores(builder, kernel32);
        return builder.build();
    }

    private WindowsCPUDetector() {
    }
}
