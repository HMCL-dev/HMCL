// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jetbrains.annotations.NotNullByDefault;

/// An operating system and architecture pair, independent of the current runtime environment.
///
/// @param os the operating system
/// @param arch the processor architecture
@NotNullByDefault
public record NativePlatform(OperatingSystem os, Architecture arch) {
    /// An unspecified operating system and architecture.
    public static final NativePlatform UNKNOWN = new NativePlatform(OperatingSystem.UNKNOWN, Architecture.UNKNOWN);

    /// Windows on x86.
    public static final NativePlatform WINDOWS_X86 = new NativePlatform(OperatingSystem.WINDOWS, Architecture.X86);

    /// Windows on x86-64.
    public static final NativePlatform WINDOWS_X86_64 = new NativePlatform(OperatingSystem.WINDOWS, Architecture.X86_64);

    /// Windows on ARM64.
    public static final NativePlatform WINDOWS_ARM64 = new NativePlatform(OperatingSystem.WINDOWS, Architecture.ARM64);

    /// Linux on x86.
    public static final NativePlatform LINUX_X86 = new NativePlatform(OperatingSystem.LINUX, Architecture.X86);

    /// Linux on x86-64.
    public static final NativePlatform LINUX_X86_64 = new NativePlatform(OperatingSystem.LINUX, Architecture.X86_64);

    /// Linux on ARM64.
    public static final NativePlatform LINUX_ARM64 = new NativePlatform(OperatingSystem.LINUX, Architecture.ARM64);

    /// Linux on 32-bit ARM.
    public static final NativePlatform LINUX_ARM32 = new NativePlatform(OperatingSystem.LINUX, Architecture.ARM32);

    /// Linux on 64-bit RISC-V.
    public static final NativePlatform LINUX_RISCV64 = new NativePlatform(OperatingSystem.LINUX, Architecture.RISCV64);

    /// Linux on LoongArch64 with the new-world ABI.
    public static final NativePlatform LINUX_LOONGARCH64 = new NativePlatform(OperatingSystem.LINUX, Architecture.LOONGARCH64);

    /// Linux on LoongArch64 with the old-world ABI.
    public static final NativePlatform LINUX_LOONGARCH64_OW = new NativePlatform(OperatingSystem.LINUX, Architecture.LOONGARCH64_OW);

    /// Linux on little-endian MIPS64.
    public static final NativePlatform LINUX_MIPS64EL = new NativePlatform(OperatingSystem.LINUX, Architecture.MIPS64EL);

    /// macOS on x86-64.
    public static final NativePlatform MACOS_X86_64 = new NativePlatform(OperatingSystem.MACOS, Architecture.X86_64);

    /// macOS on ARM64.
    public static final NativePlatform MACOS_ARM64 = new NativePlatform(OperatingSystem.MACOS, Architecture.ARM64);

    /// FreeBSD on x86-64.
    public static final NativePlatform FREEBSD_X86_64 = new NativePlatform(OperatingSystem.FREEBSD, Architecture.X86_64);

    /// Returns a platform for the given operating system and architecture, reusing predefined values where available.
    public static NativePlatform of(OperatingSystem os, Architecture arch) {
        if (os == OperatingSystem.UNKNOWN && arch == Architecture.UNKNOWN) {
            return UNKNOWN;
        }

        if (arch == Architecture.X86_64) {
            switch (os) {
                case WINDOWS:
                    return WINDOWS_X86_64;
                case MACOS:
                    return MACOS_X86_64;
                case LINUX:
                    return LINUX_X86_64;
            }
        } else if (arch == Architecture.ARM64) {
            switch (os) {
                case WINDOWS:
                    return WINDOWS_ARM64;
                case MACOS:
                    return MACOS_ARM64;
                case LINUX:
                    return LINUX_ARM64;
            }
        }

        return new NativePlatform(os, arch);
    }

    /// Returns the stable operating-system and architecture identifier used in platform directory names.
    @Override
    public String toString() {
        return os.getCheckedName() + "-" + arch.getCheckedName();
    }
}
