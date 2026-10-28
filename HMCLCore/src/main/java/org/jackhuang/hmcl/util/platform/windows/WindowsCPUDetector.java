// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.HardwareVendor;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.IntFunction;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Reads package affinities and per-core efficiency classes from Windows topology records.
@NotNullByDefault
final class WindowsCPUDetector {

    /// Reads the identity associated with a registry logical-processor index.
    private static CentralProcessor.Builder detectName(@Nullable WinReg reg, int index) {
        CentralProcessor.Builder builder = new CentralProcessor.Builder();
        if (reg == null)
            return builder;
        String key = "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\" + index;
        @Nullable Object name = reg.queryValue(WinReg.HKEY.HKEY_LOCAL_MACHINE, key, "ProcessorNameString");
        @Nullable Object vendor = reg.queryValue(WinReg.HKEY.HKEY_LOCAL_MACHINE, key, "VendorIdentifier");
        if (name instanceof String value)
            builder.setName(CentralProcessor.cleanName(value));
        if (vendor instanceof String value)
            builder.setVendor(HardwareVendor.of(value));
        return builder;
    }

    /// A core or package affinity, using group * 64 + processor number as the logical identifier.
    @NotNullByDefault
    private record Relationship(int efficiencyClass, @Unmodifiable Set<Integer> processors) {
    }

    /// Decodes a bounded native buffer; the pointer width is explicit to allow both Windows ABIs.
    static @Unmodifiable List<CentralProcessor> parseTopology(Pointer buffer, long length, int pointerSize, Architecture architecture,
                                          IntFunction<CentralProcessor.Builder> identities) {
        if (pointerSize != 4 && pointerSize != 8)
            throw new IllegalArgumentException("Invalid pointer size");
        List<Relationship> cores = new ArrayList<>();
        List<Relationship> packages = new ArrayList<>();
        for (long offset = 0; offset < length; ) {
            if (length - offset < 8)
                throw new IllegalArgumentException("Truncated processor information header");
            int relationship = buffer.getInt(offset);
            long size = Integer.toUnsignedLong(buffer.getInt(offset + 4));
            if (size < 8 || size > length - offset)
                throw new IllegalArgumentException("Invalid processor information size");
            if (relationship == WinConstants.RelationProcessorCore || relationship == WinConstants.RelationProcessorPackage) {
                if (size < 32)
                    throw new IllegalArgumentException("Truncated processor relationship");
                int count = Short.toUnsignedInt(buffer.getShort(offset + 30));
                int affinitySize = pointerSize + 8;
                if (count == 0 || 32L + (long) count * affinitySize > size)
                    throw new IllegalArgumentException("Invalid processor affinity count");
                Set<Integer> processors = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    long affinity = offset + 32L + (long) i * affinitySize;
                    long mask = pointerSize == 8 ? buffer.getLong(affinity) : Integer.toUnsignedLong(buffer.getInt(affinity));
                    int group = Short.toUnsignedInt(buffer.getShort(affinity + pointerSize));
                    for (int bit = 0; bit < pointerSize * 8; bit++) {
                        if ((mask & (1L << bit)) != 0)
                            processors.add(group * 64 + bit);
                    }
                }
                if (processors.isEmpty())
                    throw new IllegalArgumentException("Empty processor affinity");
                Relationship value = new Relationship(Byte.toUnsignedInt(buffer.getByte(offset + 9)), Set.copyOf(processors));
                (relationship == WinConstants.RelationProcessorCore ? cores : packages).add(value);
            }
            offset += size;
        }
        if (packages.isEmpty() || cores.isEmpty())
            throw new IllegalArgumentException("Missing processor topology");

        // Registry processor keys enumerate active processors across groups in ascending order.
        @Unmodifiable List<Integer> logicalIds = packages.stream().flatMap(p -> p.processors.stream()).distinct().sorted().toList();
        Set<Integer> assigned = new HashSet<>();
        List<CentralProcessor> result = new ArrayList<>();
        for (Relationship pkg : packages) {
            @Unmodifiable List<Relationship> packageCores = cores.stream().filter(c -> pkg.processors.containsAll(c.processors)).toList();
            TreeMap<Integer, Integer> classes = new TreeMap<>();
            Set<Integer> members = new HashSet<>();
            for (Relationship core : packageCores) {
                for (int processor : core.processors) {
                    if (!members.add(processor) || !assigned.add(processor))
                        throw new IllegalArgumentException("Overlapping processor topology");
                }
                classes.merge(core.efficiencyClass, 1, Integer::sum);
            }
            if (!members.equals(pkg.processors))
                throw new IllegalArgumentException("Incomplete package topology");
            int efficiency = 0;
            if (classes.size() > 1) {
                // Merge upper ARM tiers and lower x86 tiers for a coarse P/E display.
                efficiency = architecture.isX86()
                        ? packageCores.size() - classes.lastEntry().getValue()
                        : classes.firstEntry().getValue();
            }
            int first = Collections.min(pkg.processors);
            result.add(identities.apply(Collections.binarySearch(logicalIds, first))
                    .setCores(new CentralProcessor.Cores(pkg.processors.size(), packageCores.size() - efficiency, efficiency)).build());
        }
        if (assigned.size() != cores.stream().mapToInt(c -> c.processors.size()).sum())
            throw new IllegalArgumentException("Unassigned processor cores");
        return List.copyOf(result);
    }

    /// Detects package topology, retaining the registry identity if the native query fails.
    static @Unmodifiable List<CentralProcessor> detect() {
        @Nullable WinReg reg = WinReg.INSTANCE;
        @Nullable Kernel32 kernel32 = Kernel32.INSTANCE;
        if (kernel32 != null) {
            try {
                IntByReference length = new IntByReference();
                kernel32.GetLogicalProcessorInformationEx(WinConstants.RelationAll, null, length);
                for (int attempt = 0; attempt < 3 && length.getValue() > 0; attempt++) {
                    try (Memory buffer = new Memory(Integer.toUnsignedLong(length.getValue()))) {
                        if (kernel32.GetLogicalProcessorInformationEx(WinConstants.RelationAll, buffer, length)) {
                            long used = Integer.toUnsignedLong(length.getValue());
                            if (used > buffer.size())
                                throw new IllegalArgumentException("Processor information exceeds buffer");
                            return parseTopology(buffer, used, Native.POINTER_SIZE, Architecture.SYSTEM_ARCH, index -> detectName(reg, index));
                        }
                    }
                }
                LOG.warning("Failed to query processor topology: " + kernel32.GetLastError());
            } catch (Exception | LinkageError e) {
                LOG.warning("Failed to detect processor topology", e);
            }
        }
        return reg == null ? List.of() : List.of(detectName(reg, 0).build());
    }

    /// Prevents instantiation.
    private WindowsCPUDetector() {
    }
}
