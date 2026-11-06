// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static org.jackhuang.hmcl.util.platform.Bits.BIT_32;
import static org.jackhuang.hmcl.util.platform.Bits.BIT_64;

public enum Architecture {
    X86(BIT_32, "x86"),
    X86_64(BIT_64, "x86-64"),
    IA32(BIT_32, "IA-32"),
    IA64(BIT_64, "IA-64"),
    SPARC(BIT_32),
    SPARCV9(BIT_64, "SPARC V9"),
    ARM32(BIT_32),
    ARM64(BIT_64),
    MIPS(BIT_32),
    MIPS64(BIT_64),
    MIPSEL(BIT_32, "MIPSel"),
    MIPS64EL(BIT_64, "MIPS64el"),
    PPC(BIT_32, "PowerPC"),
    PPC64(BIT_64, "PowerPC-64"),
    PPCLE(BIT_32, "PowerPC (Little-Endian)"),
    PPC64LE(BIT_64, "PowerPC-64 (Little-Endian)"),
    S390(BIT_32),
    S390X(BIT_64, "S390x"),
    RISCV32(BIT_32, "RISC-V 32"),
    RISCV64(BIT_64, "RISC-V 64"),
    LOONGARCH32(BIT_32, "LoongArch32"),
    LOONGARCH64_OW(BIT_64, "LoongArch64 (old world)"),
    LOONGARCH64(BIT_64, "LoongArch64"),
    UNKNOWN(Bits.UNKNOWN, "Unknown");

    private final String checkedName;
    private final String displayName;
    private final Bits bits;

    Architecture(Bits bits) {
        this.checkedName = this.toString().toLowerCase(Locale.ROOT);
        this.displayName = this.toString();
        this.bits = bits;
    }

    Architecture(Bits bits, String displayName) {
        this.checkedName = this.toString().toLowerCase(Locale.ROOT);
        this.displayName = displayName;
        this.bits = bits;
    }

    public Bits getBits() {
        return bits;
    }

    public String getCheckedName() {
        return checkedName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isX86() {
        return this == X86 || this == X86_64;
    }

    public boolean isArm() {
        return this == ARM32 || this == ARM64;
    }

    /// Parses an architecture name without consulting the current system.
    /// Unqualified PowerPC names are big-endian; `loongarch64` denotes the new-world ABI.
    /// Returns unknown for null or unrecognized input.
    public static Architecture parseArchName(@Nullable String value) {
        if (value == null) {
            return UNKNOWN;
        }
        value = value.trim().toLowerCase(Locale.ROOT);

        return switch (value) {
            case "x8664", "x86-64", "x86_64", "amd64", "ia32e", "em64t", "x64", "intel64" -> X86_64;
            case "x8632", "x86-32", "x86_32", "x86", "i86pc", "i386", "i486", "i586", "i686", "ia32", "x32" -> X86;
            case "arm64", "aarch64" -> ARM64;
            case "arm", "arm32" -> ARM32;
            case "mips64" -> MIPS64;
            case "mips64el" -> MIPS64EL;
            case "mips", "mips32" -> MIPS;
            case "mipsel", "mips32el" -> MIPSEL;
            case "riscv", "risc-v", "riscv64" -> RISCV64;
            case "ia64", "ia64w", "itanium64" -> IA64;
            case "ia64n" -> IA32;
            case "sparcv9", "sparc64" -> SPARCV9;
            case "sparc", "sparc32" -> SPARC;
            case "ppc64", "powerpc64" -> PPC64;
            case "ppc64le", "powerpc64le" -> PPC64LE;
            case "ppc", "ppc32", "powerpc", "powerpc32" -> PPC;
            case "ppcle", "ppc32le", "powerpcle", "powerpc32le" -> PPCLE;
            case "s390" -> S390;
            case "s390x" -> S390X;
            case "loongarch32" -> LOONGARCH32;
            case "loongarch64" -> LOONGARCH64;
            case "loongarch64_ow" -> LOONGARCH64_OW;
            default -> {
                if (value.startsWith("armv7")) {
                    yield ARM32;
                }
                if (value.startsWith("armv8") || value.startsWith("armv9")) {
                    yield ARM64;
                }
                yield UNKNOWN;
            }
        };
    }
}
