// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import java.nio.file.Files;
import java.nio.file.Path;

public record Platform(OperatingSystem os, Architecture arch) {
    public static final Platform UNKNOWN = new Platform(OperatingSystem.UNKNOWN, Architecture.UNKNOWN);

    public static final Platform WINDOWS_X86 = new Platform(OperatingSystem.WINDOWS, Architecture.X86);
    public static final Platform WINDOWS_X86_64 = new Platform(OperatingSystem.WINDOWS, Architecture.X86_64);
    public static final Platform WINDOWS_ARM64 = new Platform(OperatingSystem.WINDOWS, Architecture.ARM64);

    public static final Platform LINUX_X86 = new Platform(OperatingSystem.LINUX, Architecture.X86);
    public static final Platform LINUX_X86_64 = new Platform(OperatingSystem.LINUX, Architecture.X86_64);
    public static final Platform LINUX_ARM64 = new Platform(OperatingSystem.LINUX, Architecture.ARM64);
    public static final Platform LINUX_ARM32 = new Platform(OperatingSystem.LINUX, Architecture.ARM32);
    public static final Platform LINUX_RISCV64 = new Platform(OperatingSystem.LINUX, Architecture.RISCV64);
    public static final Platform LINUX_LOONGARCH64 = new Platform(OperatingSystem.LINUX, Architecture.LOONGARCH64);
    public static final Platform LINUX_LOONGARCH64_OW = new Platform(OperatingSystem.LINUX, Architecture.LOONGARCH64_OW);
    public static final Platform LINUX_MIPS64EL = new Platform(OperatingSystem.LINUX, Architecture.MIPS64EL);

    public static final Platform MACOS_X86_64 = new Platform(OperatingSystem.MACOS, Architecture.X86_64);
    public static final Platform MACOS_ARM64 = new Platform(OperatingSystem.MACOS, Architecture.ARM64);

    public static final Platform FREEBSD_X86_64 = new Platform(OperatingSystem.FREEBSD, Architecture.X86_64);

    public static final Platform CURRENT_PLATFORM = Platform.getPlatform(OperatingSystem.CURRENT_OS, Architecture.CURRENT_ARCH);
    public static final Platform SYSTEM_PLATFORM = Platform.getPlatform(OperatingSystem.CURRENT_OS, Architecture.SYSTEM_ARCH);

    private static final boolean SUPPORTED_TRANSLATION_X86_64;

    static {
        if (SYSTEM_PLATFORM.equals(WINDOWS_ARM64)) {
            SUPPORTED_TRANSLATION_X86_64 = OperatingSystem.SYSTEM_BUILD_NUMBER >= 21277;
        } else if (SYSTEM_PLATFORM.equals(MACOS_ARM64)) {
            SUPPORTED_TRANSLATION_X86_64 = Files.isRegularFile(Path.of("/usr/libexec/rosetta/runtime"));
        } else {
            SUPPORTED_TRANSLATION_X86_64 = false;
        }
    }

    /// Returns `true` if the current platform architecture is not x86-64,
    /// but can support executing x86-64 applications through translation;
    /// otherwise, returns `false`.
    public static boolean isSupportedTranslationX86_64() {
        return SUPPORTED_TRANSLATION_X86_64;
    }

    public static Platform getPlatform(OperatingSystem os, Architecture arch) {
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

        return new Platform(os, arch);
    }

    public OperatingSystem getOperatingSystem() {
        return os;
    }

    public Architecture getArchitecture() {
        return arch;
    }

    public Bits getBits() {
        return arch.getBits();
    }

    public boolean equals(OperatingSystem os, Architecture arch) {
        return this.os == os && this.arch == arch;
    }

    @Override
    public String toString() {
        return os.getCheckedName() + "-" + arch.getCheckedName();
    }
}
